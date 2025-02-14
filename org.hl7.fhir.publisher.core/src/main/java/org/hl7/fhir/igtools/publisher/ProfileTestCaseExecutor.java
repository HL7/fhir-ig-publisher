package org.hl7.fhir.igtools.publisher;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.filters.StringInputStream;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.ProfileTestCaseExecutor.Fetched;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.utils.OperationOutcomeUtilities;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonException;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.validation.instance.InstanceValidator;
import org.hl7.fhir.validation.instance.MatchetypeValidator;
import org.hl7.fhir.validation.special.TxTesterSorters;

public class ProfileTestCaseExecutor {

  public class Fetched {

    private FetchedFile f;
    private FetchedResource r;

    public Fetched(FetchedFile f, FetchedResource r) {
      this.f = f;
      this.r = r;
    }

  }

  private String rootDir;
  private SimpleWorkerContext context;
  private InstanceValidator validator;
  private List<FetchedFile> files;
  private String localDir;

  public ProfileTestCaseExecutor(String rootDir, SimpleWorkerContext context, InstanceValidator validator, List<FetchedFile> files) {
    this.rootDir = rootDir;
    this.context = context;
    this.validator = validator;
    this.files = files;
  }

  public void execute(String filename) throws JsonException, IOException {
    String localFile = Utilities.path(rootDir, filename);
    localDir = FileUtilities.getDirectoryForFile(localFile);
    JsonObject testCases = JsonParser.parseObjectFromFile(localFile);
    JsonObject output = new JsonObject();
    for (JsonObject p : testCases.forceArray("profiles").asJsonObjects()) {
      executeProfile(p, output.forceArray("profiles").addObject());
    }
    JsonParser.compose(output, new File(FileUtilities.changeFileExt(localFile, ".out.json")), true);
  }

  private void executeProfile(JsonObject p, JsonObject output) throws JsonException, IOException {
    output.set("url", p.asString("url"));
    
    Fetched profile = findProfile(p.asString("url"));
    if (profile == null) {
      throw new FHIRException("Unable to find test profile '"+p.asString("url")+"'");
    }
    for (JsonObject tc : p.forceArray("tests").asJsonObjects()) {
      executeTestCase(profile, tc, output.forceArray("tests").addObject());
    }
  }

  private Fetched findProfile(String url) {
    for (FetchedFile f : files) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof StructureDefinition && url.equals(((StructureDefinition) r.getResource()).getUrl())) {
          return new Fetched(f, r);
        }
      }
    }
    return null;
  }

  private void executeTestCase(Fetched profile, JsonObject tc, JsonObject output) throws IOException {
    output.set("source", tc.asString("source"));
    InputStream cnt = loadSource(tc.asString("source"));
    output.set("description", tc.asString("description"));
    List<ValidationMessage> messages = new ArrayList<>();
    List<StructureDefinition> profiles = new ArrayList<>();
    profiles.add((StructureDefinition) profile.r.getResource());
    
    FhirFormat fmt = tc.asString("source").contains(".xml") ? FhirFormat.XML : FhirFormat.JSON;
    validator.validate(null, messages, cnt, fmt, profiles);
    boolean passes = true;
    for (ValidationMessage vm : messages) {
      passes = passes && !vm.isError();
    }
    OperationOutcome oo = OperationOutcomeUtilities.createOutcome(messages);
    TxTesterSorters.sortOperationOutcome(oo);    
    output.set("valid", passes);
    String js = new org.hl7.fhir.r5.formats.JsonParser().composeString(oo);
    JsonObject ooj = JsonParser.parseObject(js);
    output.set("outcome", ooj);
    
    boolean passesTest = true;
    if (passes != tc.asBoolean("valid")) {
      passesTest = false;
    }
    if (tc.has("outcome")) {
      MatchetypeValidator mv = new MatchetypeValidator(validator.getFHIRPathEngine());
      Element actual = Manager.parseSingle(context, new StringInputStream(js), FhirFormat.JSON);
      byte[] ec = JsonParser.composeBytes(tc.get("outcome"));
      Element expected = Manager.parseSingle(context, new ByteArrayInputStream(ec), FhirFormat.JSON);
      messages = new ArrayList<>();
      mv.setPatternMode(true);
      mv.compare(messages, "outcome", expected, actual);
      OperationOutcome moo = OperationOutcomeUtilities.createOutcome(messages);
      passes = true;
      for (ValidationMessage vm : messages) {
        passes = passes && !vm.isError();
      }
      if (!passes) {
        passesTest = false;
        js = new org.hl7.fhir.r5.formats.JsonParser().composeString(moo);
        ooj = JsonParser.parseObject(js);
        output.set("outcome-test", ooj);
      }
    }
    if (!passesTest) {
      profile.f.getErrors().add(new ValidationMessage(Source.InstanceValidator, org.hl7.fhir.utilities.validation.ValidationMessage.IssueType.INVALID, "StructureDefinition",
          "Profile test case failed - test for "+tc.asString("source")+" "+b(passes)+ " but it should have "+b(tc.asBoolean("valid")), IssueSeverity.ERROR));
    }
  }

  private String b(boolean value) {
    return value ? "passed" : "failed";
  }

  private InputStream loadSource(String filename) throws IOException {
    filename = filename.replace("\\",  "/");
    File f = null;
    if (filename.startsWith("/")) {
      f = new File(Utilities.path(rootDir, filename));
    } else {
      f = new File(Utilities.path(localDir, filename));
    }
    if (!f.exists()) {
      throw new FHIRException("Test Case Source "+filename+" not found at "+f.getAbsolutePath());
    }
    return new FileInputStream(f);
  }

}
