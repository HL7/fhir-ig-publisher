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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.igtools.publisher.utils.IGReleaseUpdater.ServerType;
import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.Utilities;

import com.google.gson.JsonSyntaxException;

/**
 * This assumes the web site is laid out as documented on Confluence
 * 
 * @author graha
 *
 */
public class IGWebSiteMaintainer {

  public static void main(String[] args) throws FileNotFoundException, IOException, JsonSyntaxException, ParseException {
    execute(args[0], args.length > 1 ? new IGRegistryMaintainer(args[1]) : null, args.length >= 3 && "true".equals(args[2]), null);
  }
  
  public static void execute(String folder, IGRegistryMaintainer reg, boolean doCore, String filter) throws FileNotFoundException, IOException, JsonSyntaxException, ParseException {
    File f = new File(folder);
    if (!f.exists())
      throw new IOException("Folder "+folder+" not found");
    if (!f.isDirectory())
      throw new IOException("The path "+folder+" is not a folder");
    
    f = new File(Utilities.path(folder, "publish.ini"));
    if (!f.exists())
      throw new IOException("publish.ini not found in "+folder);
    
    IniFile ini = new IniFile(f.getAbsolutePath());
    if (!"fhir.layout".equals(ini.getStringProperty("website", "style")))
      throw new IOException("publish.ini in "+f.getAbsolutePath()+" not in the correct format (missing style=fhir.layout in [website])");
      
    String url = ini.getStringProperty("website",  "url");
    if (reg == null && !ini.getBooleanProperty("website", "no-registry"))
      throw new Error("This web site contains IGs that are registered in the implementation guide registry, and you must pass in a reference to the registry");
    ServerType serverType = ServerType.fromCode(ini.getStringProperty("website", "server"));
    
    File sft = null;
    if (ini.hasProperty("website", "search-template")) {
      sft = new File(Utilities.path(folder, ini.getStringProperty("website", "search-template")));
      if (!sft.exists()) {
        throw new Error("Search form "+sft.getAbsolutePath()+" not found");
      }
    }
    System.out.println("Update the website at "+folder);
    System.out.println("The public URL is at "+url);
    if (reg == null)
      System.out.println("The public registry will not be updated");
    else
      System.out.println("Update the public registry at "+reg.getPath());
    System.out.println("The server type is "+serverType);
    System.out.println("looking for IGs in "+folder);
    List<String> igs = scanForIgs(folder, doCore);
    System.out.println("found "+igs.size()+" IGs to update:");
    for (String s : igs) {
      System.out.println(" - "+s);
    }
    if (filter == null ) {
      System.out.print("Enter y to continue: ");    
      int r = System.in.read();
      if (r != 'y')
        return;
    }
    
    Map<String, IndexMaintainer> indexes = new HashMap<>();
    if (ini.hasSection("indexes")) {
      for (String realm : ini.getPropertyNames("indexes")) {
        String[] p = ini.getStringProperty("indexes", realm).split("\\;");
        indexes.put(realm, new IndexMaintainer(realm, p[0], Utilities.path(folder, p[1]), Utilities.path(folder, ini.getStringProperty("website", "index-template"))));        
      }
    }
    for (String s : igs) {
      new IGReleaseUpdater(s, url, folder, reg, serverType, igs, sft, filter == null || filter.equals(s)).check(indexes);
    }
    for (IndexMaintainer index : indexes.values()) {
      index.execute();
    }
    System.out.println("==================== ");
    System.out.println("Processing Feeds for "+folder);
    if (!Utilities.noString(ini.getStringProperty("feeds",  "package")) || !Utilities.noString(ini.getStringProperty("feeds",  "publication"))) {
      new FeedBuilder().execute(folder, Utilities.path(folder, ini.getStringProperty("feeds", "package")), Utilities.path(folder, ini.getStringProperty("feeds", "publication")), ini.getStringProperty("website", "org"), Utilities.pathURL(url, ini.getStringProperty("feeds", "package")), url);
    }
    System.out.println("Finished Processing Feeds");
    System.out.println("==================== ");
    reg.finish();
  }
  
  public static List<String> scanForIgs(String folder, boolean doCore) throws IOException {
    File f = new File(Utilities.path(folder, "publish.ini"));
    if (f.exists() && !doCore) {
      IniFile ini = new IniFile(f.getAbsolutePath());
      if (ini.hasSection("ig-dirs")) {
        List<String> igs = new ArrayList<>();
        for (String s: ini.getPropertyNames("ig-dirs")) {
          igs.addAll(scanForIgs(new File(Utilities.path(folder, s)), false, false));
        }
        return igs;
      }
    }
    return scanForIgs(new File(folder), true, doCore);
  }
  
  public static List<String> scanForIgs(File folder, boolean root, boolean doCore) throws IOException {
    List<String> igs = new ArrayList<>();
    boolean isIg = false;
    for (File f : folder.listFiles()) {
      if (f.getName().equals("package-list.json"))
        isIg = true;
    }
    if (isIg && (doCore || !new File(Utilities.path(folder.getAbsolutePath(), "directory.template")).exists())) {
        igs.add(folder.getAbsolutePath());
    }
    if (!isIg || root) {
      for (File f : folder.listFiles()) {
        if (f.isDirectory())
          igs.addAll(scanForIgs(f, false, false));
      }
    }
    return igs;
  }
  
  
}
