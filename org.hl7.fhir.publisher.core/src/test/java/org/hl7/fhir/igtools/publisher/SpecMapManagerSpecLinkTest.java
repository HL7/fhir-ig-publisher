package org.hl7.fhir.igtools.publisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.utilities.Utilities;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * isSpecLink gives the same answers as the checks HTMLInspector.checkTarget used to make for each spec
 */
class SpecMapManagerSpecLinkTest {

  private static final String[] BASES = { "http://hl7.org/fhir/us/core", "http://hl7.org/fhir/us/core/", "http://example.org", "", "/", "http://example.org//" };
  private static final String[] BASE2S = { null, "http://hl7.org/fhir/us/core/STU6.1", "http://hl7.org/fhir/us/core/STU6.1/" };
  private static final String[] FILES = { "", "definitions.json.zip", "definitions.xml.zip", "full-ig.zip", "package.tgz", "history.html",
      "toc.html", "qa.html", "StructureDefinition-us-core-patient.html", "StructureDefinition-us-core-patient.html#profile",
      "index.html", "other.html", "x/package.tgz" };

  // what checkTarget did before isSpecLink. A link's trailing / was removed before these checks. Left out:
  // (spec.getBase()+"/").equals(rref), dropped on review, which only matched a link that ended in //
  private static boolean oldCheck(SpecMapManager spec, String rref) {
    if (spec.getBase() != null && (spec.getBase().equals(rref) || (spec.getBase()).equals(rref+"/") || spec.hasTarget(rref) ||
            Utilities.existsInList(rref, Utilities.pathURL(spec.getBase(), "definitions.json.zip"),
                    Utilities.pathURL(spec.getBase(), "full-ig.zip"), Utilities.pathURL(spec.getBase(), "definitions.xml.zip"),
                    Utilities.pathURL(spec.getBase(), "package.tgz"), Utilities.pathURL(spec.getBase(), "history.html")))) {
      return true;
    }
    if (spec.getBase2() != null && (spec.getBase2().equals(rref) || (spec.getBase2()).equals(rref+"/") ||
            Utilities.existsInList(rref, Utilities.pathURL(spec.getBase2(), "definitions.json.zip"), Utilities.pathURL(spec.getBase2(), "definitions.xml.zip"), Utilities.pathURL(spec.getBase2(), "package.tgz"), Utilities.pathURL(spec.getBase2(), "full-ig.zip")))) {
      return true;
    }
    return false;
  }

  private static SpecMapManager spec(String base, String base2) throws IOException {
    String json = "{\"webUrl\" : \"" + base + "\", \"paths\" : {\"http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient\" : \"StructureDefinition-us-core-patient.html\"}, \"targets\" : [\"index.html\"]}";
    SpecMapManager spec = new SpecMapManager(json.getBytes(StandardCharsets.UTF_8), "4.0.1", "4.0.1");
    if (base2 != null) {
      spec.setBase2(base2);
    }
    return spec;
  }

  private static List<String> links(String... bases) {
    List<String> links = new ArrayList<>();
    for (String base : bases) {
      if (base == null) {
        continue;
      }
      for (String file : FILES) {
        links.add(file.isEmpty() ? base : Utilities.pathURL(base, file));
        // a file name glued to the base without a /
        links.add(base + file);
        // one / too many
        links.add(base + "/" + file);
      }
      // a link that ends in //, which keeps one / after checkTarget removes the last
      links.add(base + "//");
    }
    links.add("http://hl7.org/fhir/us/core-x/package.tgz");
    links.add("http://hl7.org/fhir/us/corepackage.tgz");
    links.add("package.tgz");
    links.add("http://other.org/package.tgz");
    // checkTarget removes a trailing / before it gets this far
    links.replaceAll(l -> l.endsWith("/") ? l.substring(0, l.length()-1) : l);
    return links;
  }

  @Test
  void sameAsTheOldChecks() throws IOException {
    int count = 0;
    for (String base : BASES) {
      for (String base2 : BASE2S) {
        SpecMapManager spec = spec(base, base2);
        for (String link : links(BASES[0], BASES[1], BASES[2], BASES[4], BASES[5], BASE2S[1], BASE2S[2])) {
          Assertions.assertEquals(oldCheck(spec, link), spec.isSpecLink(link), "base " + base + ", base2 " + base2 + ", link " + link);
          count++;
        }
      }
    }
    Assertions.assertTrue(count > 1000);
  }

  @Test
  void specFiles() throws IOException {
    SpecMapManager spec = spec("http://hl7.org/fhir/us/core", "http://hl7.org/fhir/us/core/STU6.1");
    Assertions.assertTrue(spec.isSpecLink("http://hl7.org/fhir/us/core"));
    Assertions.assertTrue(spec.isSpecLink("http://hl7.org/fhir/us/core/package.tgz"));
    Assertions.assertTrue(spec.isSpecLink("http://hl7.org/fhir/us/core/history.html"));
    Assertions.assertTrue(spec.isSpecLink("http://hl7.org/fhir/us/core/STU6.1"));
    Assertions.assertTrue(spec.isSpecLink("http://hl7.org/fhir/us/core/STU6.1/full-ig.zip"));
    Assertions.assertTrue(spec.isSpecLink("http://hl7.org/fhir/us/core/StructureDefinition-us-core-patient.html"));
    // history.html is only published at the canonical base
    Assertions.assertFalse(spec.isSpecLink("http://hl7.org/fhir/us/core/STU6.1/history.html"));
    Assertions.assertFalse(spec.isSpecLink("http://hl7.org/fhir/us/core/other.html"));
    Assertions.assertFalse(spec.isSpecLink("http://hl7.org/fhir/us/corepackage.tgz"));
  }
}
