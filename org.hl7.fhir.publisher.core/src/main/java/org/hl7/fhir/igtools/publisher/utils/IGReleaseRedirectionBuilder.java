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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class IGReleaseRedirectionBuilder {

  private static final String ASP_TEMPLATE = "<%@ language=\"javascript\"%>\r\n"+
      "\r\n"+
      "<%\r\n"+
      "  var s = String(Request.ServerVariables(\"HTTP_ACCEPT\"));\r\n"+
      "  if (s.indexOf(\"json\") > -1) \r\n"+
      "    Response.Redirect(\"{{literal}}.json\");\r\n"+
      "  else if (s.indexOf(\"html\") > -1) \r\n"+
      "    Response.Redirect(\"{{html}}\");\r\n"+
      "  else\r\n"+
      "    Response.Redirect(\"{{literal}}.xml\");\r\n"+
      "\r\n"+
      "%>\r\n"+
      "\r\n"+
      "<!DOCTYPE html>\r\n"+
      "<html>\r\n"+
      "<body>\r\n"+
      "You should not be seeing this page. If you do, ASP has failed badly.\r\n"+
      "</body>\r\n"+
      "</html>\r\n";
  private static final String PHP_TEMPLATE = "<?php\r\n"+
      "function Redirect($url)\r\n"+
      "{\r\n"+
      "  header('Location: ' . $url, true, 302);\r\n"+
      "  exit();\r\n"+
      "}\r\n"+
      "\r\n"+
      "$accept = $_SERVER['HTTP_ACCEPT'];\r\n"+
      "if (strpos($accept, 'json') !== false)\r\n"+
      "  Redirect('{{literal}}.json');\r\n"+
      "elseif (strpos($accept, 'html') !== false)\r\n"+
      "  Redirect('{{html}}');\r\n"+
      "else \r\n"+
      "  Redirect('{{literal}}.xml');\r\n"+
      "?>\r\n"+
      "    \r\n"+
      "You should not be seeing this page. If you do, PHP has failed badly.\r\n";

  private String folder;
  private String canonical;
  private String vpath;
  private int countTotal;
  private int countUpdated;
  private NpmPackage pkg;

  public IGReleaseRedirectionBuilder(String folder, String canonical, String vpath) {
   this.folder = folder; 
   this.canonical = canonical;
   this.vpath = vpath;
   countTotal = 0;
   countUpdated = 0;
  }

  public void buildApacheRedirections() throws IOException {    
    Map<String, String> map = createMap();
    if (map != null) {
      for (String s : map.keySet()) {
        String path = Utilities.path(folder, s, "index.php");
        String p = s.replace("/", "-");
        String litPath = Utilities.path(folder, p);
        if (new File(litPath+".xml").exists() && new File(litPath+".json").exists()) 
          createPhpRedirect(path, map.get(s), Utilities.pathURL(vpath, p));
      }
    }
  }
  
  public void buildAspRedirections() throws IOException {
    Map<String, String> map = createMap();
    if (map != null) {
      for (String s : map.keySet()) {
        String path = Utilities.path(folder, s, "index.asp");
        String p = s.replace("/", "-");
        String litPath = Utilities.path(folder, p);
        if (new File(litPath+".xml").exists() && new File(litPath+".json").exists()) 
          createAspRedirect(path, map.get(s), Utilities.pathURL(vpath, p));
      }
    }
  }

  private void createAspRedirect(String path, String urlHtml, String urlSrc) throws IOException {
    String t = ASP_TEMPLATE;
    t = t.replace("{{html}}", urlHtml);
    t = t.replace("{{literal}}", urlSrc);
    Utilities.createDirectory(Utilities.getDirectoryForFile(path));
    countTotal++;
    if (!new File(path).exists() || !TextFile.fileToString(path).equals(t)) {
      TextFile.stringToFile(t, path, false);
      countUpdated++;
    }
  }

  private void createPhpRedirect(String path, String urlHtml, String urlSrc) throws IOException {
    String t = PHP_TEMPLATE;
    t = t.replace("{{html}}", urlHtml);
    t = t.replace("{{literal}}", urlSrc);
    Utilities.createDirectory(Utilities.getDirectoryForFile(path));
    countTotal++;
    if (!new File(path).exists() || !TextFile.fileToString(path).equals(t)) {
      TextFile.stringToFile(t, path, false);
      countUpdated++;
    }
  }

  private Map<String, String> createMap() throws IOException {
    File f = new File(Utilities.path(folder, "package.tgz"));
    if (!f.exists())
      return null;
    pkg = NpmPackage.fromPackage(new FileInputStream(f));
    JsonObject json = null;
    try {
      json = JsonTrackingParser.parseJson(pkg.load("other", "spec.internals"));
    } catch (Exception e) {
      return null;
    }
    Map<String, String> res = new HashMap<>();
    for (Entry<String, JsonElement> p : json.getAsJsonObject("paths").entrySet()) {
      String key = p.getKey();
      if (key.contains("|"))
        key = key.substring(0,  key.indexOf("|"));
      if (key.length() >= canonical.length()+1 && key.startsWith(canonical)) {
      String value = p.getValue().getAsString();
      res.put(key.substring(canonical.length()+1), Utilities.pathURL(vpath, value));
      }
    }
    return res;
  }

  public int getCountTotal() {
    return countTotal;
  }

  public int getCountUpdated() {
    return countUpdated;
  }

  public String getFhirVersion() {
    return pkg == null ? null : pkg.fhirVersion();
  }


  
}
