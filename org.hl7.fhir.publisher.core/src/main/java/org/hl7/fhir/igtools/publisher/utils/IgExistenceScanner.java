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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.igtools.publisher.utils.IGReleaseUpdater.ServerType;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;

import com.google.gson.JsonSyntaxException;

public class IgExistenceScanner {

  public static void main(String[] args) throws FileNotFoundException, IOException, JsonSyntaxException, ParseException {
    execute(args[0], args.length > 2 ? new IGRegistryMaintainer(args[2]) : null);
  }
  
  public static void execute(String folder, IGRegistryMaintainer reg) throws FileNotFoundException, IOException, JsonSyntaxException, ParseException {
    File f = new File(folder);
    if (!f.exists())
      throw new IOException("Folder "+folder+" not found");
    if (!f.isDirectory())
      throw new IOException("The path "+folder+" is not a folder");
    if (!f.exists())
      throw new IOException("publish.ini not found in "+folder);
    
    f = new File(Utilities.path(folder, "publish.ini"));
    IniFile ini = new IniFile(f.getAbsolutePath());
    if ("fhir.layout".equals(ini.getStringProperty("website", "style")))
      throw new IOException("publish.ini in "+folder+" not in the correct format (missing style=fhir.layout in [website])");
      
    String url = ini.getStringProperty("website",  "url");
    if (reg == null && !ini.getBooleanProperty("website", "no-registry"))
      throw new Error("This web site contains IGs that are registered in the implementation guide registry, and you must pass in a reference to the registry");
    ServerType serverType = ServerType.fromCode(ini.getStringProperty("website", "server"));
    
    System.out.println("Update the website at "+folder);
    System.out.println("The public URL is at "+folder);
    if (reg == null)
      System.out.println("The public registry will not be updated");
    else
      System.out.println("Update the public registry at "+reg.getPath());
    System.out.println("The server type is "+serverType);
    System.out.print("Enter y to continue");    
    int r = System.in.read();
    if (r != 'y')
      return;
    
    System.out.println("looking for IGs in "+folder);
    List<String> igs = scanForIgs(folder);
    System.out.println("found: ");
    for (String s : igs) {
      System.out.println("  "+s);
      new IGReleaseUpdater(s, url, folder, reg, serverType).check();
    }
    System.out.println("==================== ");
    System.out.println("Processing Feeds for "+folder);
    if (!Utilities.noString(ini.getStringProperty("feeds",  "package"))) {
      new FeedBuilder().execute(folder, ini.getStringProperty("feeds", "package"), ini.getStringProperty("website", "org"), url, true);
    }
    if (!Utilities.noString(ini.getStringProperty("feeds",  "publication"))) {
      new FeedBuilder().execute(folder, ini.getStringProperty("feeds", "publication"), ini.getStringProperty("website", "org"), url, false);
    }
    System.out.println("Finished Processing Feeds");
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
