package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;


public class HL7OrgFhirCompareFixer {

  private static final int MAX_COMMIT = 200000;

  public static void main(String[] args) throws IOException {
    File folderRoot = new File("/Users/grahamegrieve/web/www.hl7.org.fhir");
    new HL7OrgFhirCompareFixer().execute(folderRoot, folderRoot, true);
  }

  private int counter;
  
  private void execute(File rootFolder, File folder, boolean root) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        if (root) {
          System.out.println("Process "+f.getAbsolutePath());
        }
        if (!root || !Utilities.existsInList(f.getName(), "us", "uv")) {
          execute(rootFolder, f, false);
        }
      } else if (f.getName().endsWith(".html")) {
        processCompare(rootFolder, f);
      }
    }
  }

  private void processCompare(File rootFolder, File f) throws FileNotFoundException, IOException {
    String s = FileUtilities.fileToString(f);
    if (s.contains("http://services.w3.org/htmldiff?")) {
      s = s.replace("http://services.w3.org/htmldiff?doc1=", "https://www5.aptest.com/standards/htmldiff/htmldiff.pl?oldfile=");
      s = s.replace("&amp;doc2=", "&amp;newfile=");
      FileUtilities.stringToFile(s, f);
      counter++;
      if (counter >= MAX_COMMIT) {
        throw new Error("terminating - max count reached");
      }
    }
  }
}

