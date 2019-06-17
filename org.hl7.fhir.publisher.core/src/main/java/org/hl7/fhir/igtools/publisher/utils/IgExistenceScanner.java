package org.hl7.fhir.igtools.publisher.utils;

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

public class IgExistenceScanner {

  public static void main(String[] args) throws FileNotFoundException, IOException {
    System.out.println("looking for IGs in "+args[0]);
    List<String> igs = scanForIgs(args[0]);
    IGRegistryMaintainer reg = new IGRegistryMaintainer(args.length > 2 ? args[2] : null);
    System.out.println("found: ");
    for (String s : igs) {
      System.out.println(s);
      new IGReleaseUpdater(s, args[1], args[0], reg, null).check();
    }
    System.out.println("==================== ");
    reg.finish();
  }
  
  public static List<String> scanForIgs(String folder) {
    return scanForIgs(new File(folder), true);
  }
  
  public static List<String> scanForIgs(File folder, boolean isRoot) {
    List<String> igs = new ArrayList<>();
    boolean isIg = false;
    for (File f : folder.listFiles()) {
      if (f.getName().equals("package-list.json") && !isRoot)
        isIg = true;
    }
    if (isIg)
        igs.add(folder.getAbsolutePath());
    else { 
      for (File f : folder.listFiles()) {
        if (f.isDirectory())
          igs.addAll(scanForIgs(f, false));
      }
    }
    return igs;
  }
}
