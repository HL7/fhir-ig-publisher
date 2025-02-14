package org.hl7.fhir.igtools.web;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;

/**
 * This deletes all the current files for a directory that contains the root of an IG without deleting the hitory infrastructure or past versions
 * 
 * @author graha
 *
 */
public class IGReleaseVersionDeleter {

  public void clear(String folder, String historyRepo) throws IOException, FHIRException {
    List<String> igs = listIgs(folder);
    List<String> hist = listHistoryFolder(historyRepo);
    hist.add("package-list.json");
    hist.add("publish.ini");
    int i = 0;
    for (File f : new File(folder).listFiles()) {
      String rn = f.getAbsolutePath().substring(folder.length()+1);
      if (!igs.contains(rn) && !hist.contains(rn) && !rn.endsWith(".template")) {
          System.out.println("Delete "+rn);
          if (f.isDirectory())
            FileUtilities.clearDirectory(f.getAbsolutePath());
          f.delete();
          i++;
      }
    }
    System.out.println("Cleaned current release of "+folder+": deleted "+i+" files");
  }

  private List<String> listHistoryFolder(String historyRepo) {
    List<String> files = new ArrayList<>();
    listFiles(files, historyRepo, new File(historyRepo));
    return files;
  }

  private void listFiles(List<String> files, String base, File folder) {
    for (File f : folder.listFiles()) {
      if (!f.getName().equals(".git")) {
        files.add(f.getAbsolutePath().substring(base.length()+1));
        if (f.isDirectory())
          listFiles(files, base, f);
      }
    }
  }

  private List<String> listIgs(String folder) throws IOException, FHIRException {
    String pl = Utilities.path(folder, "package-list.json");
    if (!new File(pl).exists())
      throw new FHIRException("Folder '"+folder+"' is not a valid IG directory");
    JsonObject json = JsonParser.parseObjectFromFile(pl);
    List<String> igs = new ArrayList<>();
    String canonical = json.asString("canonical");
    for (JsonObject obj : json.getJsonObjects("list")) {
      String path = obj.asString("path");
      if (path.startsWith(canonical)) {
        igs.add(path.substring(canonical.length()+1));
      }
    }
    return igs;
  }
  

  public static void main(String[] args) throws FileNotFoundException, IOException, FHIRException {
    new IGReleaseVersionDeleter().clear(args[0], args[1]);
  }
  
}
