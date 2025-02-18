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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class IGReleaseRedirectionBuilder {

  private static final String ASP_TEMPLATE = "<%@ language=\"javascript\"%>\r\n"+
      "\r\n"+
      "<%\r\n"+
      "  var s = String(Request.ServerVariables(\"HTTP_ACCEPT\"));\r\n"+
      "  if (s.indexOf(\"application/json+fhir\") > -1) \r\n"+
      "    Response.Redirect(\"{{literal}}.json2\");\r\n"+
      "  else if (s.indexOf(\"application/fhir+json\") > -1) \r\n"+
      "    Response.Redirect(\"{{literal}}.json1\");\r\n"+
      "  else if (s.indexOf(\"application/xml+fhir\") > -1) \r\n"+
      "    Response.Redirect(\"{{literal}}.xml2\");\r\n"+
      "  else if (s.indexOf(\"application/fhir+xml\") > -1) \r\n"+
      "    Response.Redirect(\"{{literal}}.xml1\");\r\n"+
      "  else if (s.indexOf(\"json\") > -1) \r\n"+
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
      "if (strpos($accept, 'application/json+fhir') !== false)\r\n"+
      "  Redirect('{{literal}}.json2');\r\n"+
      "elseif (strpos($accept, 'application/fhir+json') !== false)\r\n"+
      "  Redirect('{{literal}}.json1');\r\n"+
      "elseif (strpos($accept, 'json') !== false)\r\n"+
      "  Redirect('{{literal}}.json');\r\n"+
      "elseif (strpos($accept, 'application/xml+fhir') !== false)\r\n"+
      "  Redirect('{{literal}}.xml2');\r\n"+
      "elseif (strpos($accept, 'application/fhir+xml') !== false)\r\n"+
      "  Redirect('{{literal}}.xml1');\r\n"+
      "elseif (strpos($accept, 'html') !== false)\r\n"+
      "  Redirect('{{html}}');\r\n"+
      "else \r\n"+
      "  Redirect('{{literal}}.xml');\r\n"+
      "?>\r\n"+
      "    \r\n"+
      "You should not be seeing this page. If you do, PHP has failed badly.\r\n";

  private static final String HTML_TEMPLATE = "<?php\r\n"+
      "function Redirect($url)\r\n"+
      "{\r\n"+
      "  header('Location: ' . $url, true, 302);\r\n"+
      "  exit();\r\n"+
      "}\r\n"+
      "\r\n"+
      "$accept = $_SERVER['HTTP_ACCEPT'];\r\n"+
      "if (strpos($accept, 'application/json+fhir') !== false)\r\n"+
      "  Redirect('{{literal}}.json2');\r\n"+
      "elseif (strpos($accept, 'application/fhir+json') !== false)\r\n"+
      "  Redirect('{{literal}}.json1');\r\n"+
      "elseif (strpos($accept, 'json') !== false)\r\n"+
      "  Redirect('{{literal}}.json');\r\n"+
      "elseif (strpos($accept, 'application/xml+fhir') !== false)\r\n"+
      "  Redirect('{{literal}}.xml2');\r\n"+
      "elseif (strpos($accept, 'application/fhir+xml') !== false)\r\n"+
      "  Redirect('{{literal}}.xml1');\r\n"+
      "elseif (strpos($accept, 'html') !== false)\r\n"+
      "  Redirect('{{html}}');\r\n"+
      "else \r\n"+
      "  Redirect('{{literal}}.xml');\r\n"+
      "?>\r\n"+
      "    \r\n"+
      "You should not be seeing this page. If you do, PHP has failed badly.\r\n";
  
  private static final String WC_START_ROOT = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + 
      "<configuration>\n" + 
      "  <system.webServer>\n" +
      "    <defaultDocument>\n" +
      "      <files>\n" +
      "        <add value=\"index.asp\" />\n" +
      "      </files>\n" +
      "    </defaultDocument>\n" +    
      "    <staticContent>\n" +
      "      <remove fileExtension=\".html\" />\n" +
      "      <mimeMap fileExtension=\".html\" mimeType=\"text/html;charset=UTF-8\" />\n" +
      "    </staticContent>\n" +
      "    <rewrite>\n" + 
      "      <rules>\n";
  
  private static final String WC_START_OTHER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + 
      "<configuration>\n" + 
      "  <system.webServer>\n" +
      "    <staticContent>\n" +
      "      <remove fileExtension=\".html\" />\n" +
      "      <mimeMap fileExtension=\".html\" mimeType=\"text/html;charset=UTF-8\" />\n" +
      "    </staticContent>\n" +
      "    <rewrite>\n" + 
      "      <rules>\n";
  
  private static final String WC_END = "      </rules>\n" + 
      "    </rewrite>\n" + 
      "  </system.webServer>\n" + 
      "</configuration>";
  
  private String folder;
  private String canonical;
  private String vpath;
  private String localFolder;
  private String websiteRootFolder;
  private int countTotal;
  private int countUpdated;
  private NpmPackage pkg;

  public IGReleaseRedirectionBuilder(String folder, String canonical, String vpath, String websiteRootFolder) {
   this.folder = folder; 
   this.canonical = canonical;
   this.vpath = vpath;
   this.websiteRootFolder = websiteRootFolder;
   localFolder = folder.substring(websiteRootFolder.length());
   countTotal = 0;
   countUpdated = 0;
  }

  public void buildApacheRedirections() throws IOException {    
    Map<String, String> map = createMap(false);
    if (map != null) {
      for (String s : map.keySet()) {
        if (!s.contains(":")) {
          String path = Utilities.path(folder, s, "index.php");
          String p = s.replace("/", "-");
          String litPath = Utilities.path(folder, p);
          if (new File(litPath+".xml").exists() && new File(litPath+".json").exists()) 
            createPhpRedirect(path, map.get(s), Utilities.pathURL(vpath, p));
        }
      }
    }
  }
  
  public void buildCloudRedirections() throws IOException {    
    Map<String, String> map = createMap(false);
    if (map != null) {
      for (String s : map.keySet()) {
        if (!s.contains(":")) {
          String path = Utilities.path(folder, s, "index.html");
          String p = s.replace("/", "-");
          String litPath = Utilities.path(folder, p);
          if (new File(litPath+".xml").exists() && new File(litPath+".json").exists()) 
            createHtmlRedirect(path, map.get(s), Utilities.pathURL(vpath, p));
        }
      }
    }
  }
  
  public void buildNewAspRedirections(boolean isCore, boolean isCoreRoot) throws IOException {
    Map<String, String> map = createMap(isCore);
    if (map == null) {
      System.out.println("!!! no map, "+folder);
    } else {
      // we need to generate a web config, and the redirectors
      Set<String> rtl = listResourceTypes(map);
      if (rtl.isEmpty()) {
        if (!folder.contains("smart-app-launch") && !folder.contains("davinci-deqm")) {
          System.out.println("!!! empty map, "+folder);
        }
      } else {
        generateWebConfig(rtl, isCoreRoot);
        for (String rt : rtl) {
          generateRedirect(rt, map);
        }
        if (isCoreRoot) {
          for (String s : map.keySet()) {
            if (!s.contains("/") && !s.contains(":")) {
              String path = Utilities.path(folder, s, "index.asp");
              String p = s.replace("/", "-");
              String litPath = Utilities.path(folder, p)+".html";
              if (!new File(litPath+".xml").exists() && !new File(litPath+".json").exists()) { 
                litPath = Utilities.path(folder, tail(map.get(s)));
              } File file = new File(FileUtilities.changeFileExt(litPath, ".xml"));
              if (file.exists() && new File(FileUtilities.changeFileExt(litPath, ".json")).exists()) {
                createAspRedirect(path, map.get(s), Utilities.pathURL(vpath, head(file.getName())));
              }
            }
          }
        }
      }
    }
  }

  private void generateRedirect(String rt, Map<String, String> map) throws IOException {
    if (!rt.contains(":")) {      
      StringBuilder b = new StringBuilder();
      countTotal++;

      String root = Utilities.pathURL(canonical, rt);
      b.append("<%@ language=\"javascript\"%>\r\n" + 
          "\r\n" + 
          "<%\r\n" + 
          "  var s = String(Request.ServerVariables(\"HTTP_ACCEPT\"));\r\n" +
          "  var id = Request.QueryString(\"id\");\r\n"+
          "  if (s.indexOf(\"application/json+fhir\") > -1) \r\n" + 
          "    Response.Redirect(\""+root+"-\"+id+\".json2\");\r\n" + 
          "  else if (s.indexOf(\"application/fhir+json\") > -1) \r\n" + 
          "    Response.Redirect(\""+root+"-\"+id+\".json1\");\r\n" + 
          "  else if (s.indexOf(\"application/xml+fhir\") > -1) \r\n" + 
          "    Response.Redirect(\""+root+"-\"+id+\".xml2\");\r\n" + 
          "  else if (s.indexOf(\"application/fhir+xml\") > -1) \r\n" + 
          "    Response.Redirect(\""+root+"-\"+id+\".xml1\");\r\n" + 
          "  else if (s.indexOf(\"json\") > -1) \r\n" + 
          "    Response.Redirect(\""+root+"-\"+id+\".json\");\r\n" + 
          "  else if (s.indexOf(\"html\") == -1) \r\n" + 
          "    Response.Redirect(\""+root+"-\"+id+\".xml\");\r\n" );
      for (String s : map.keySet()) {
        if (s.startsWith(rt+"/")) {
          String id = s.substring(rt.length()+1);
          String link = map.get(s);
          b.append("  else if (id == \""+id+"\")\r\n" + 
              "    Response.Redirect(\""+link+"\");\r\n");
        }
      }
      b.append("  else if (id == \"index\")\r\n" + 
          "    Response.Redirect(\""+root+".html\");\r\n");
      b.append(     
          "\r\n" + 
              "%>\r\n" + 
              "\r\n" + 
              "<!DOCTYPE html>\r\n" + 
              "<html>\r\n" + 
              "<body>\r\n" + 
              "Internal Error - unknown id <%= Request.QueryString(\"id\") %> (from "+Utilities.path(localFolder, "cr"+rt.toLowerCase()+".asp")+") .\r\n" + 
              "</body>\r\n" + 
          "</html>\r\n");

      String asp = b.toString();
      File f = new File(Utilities.path(folder, "cr"+rt.toLowerCase()+".asp"));
      if (f.exists()) {
        String aspc = FileUtilities.fileToString(f);
        if (aspc.equals(asp))
          return;
      }
      countUpdated++;
      FileUtilities.stringToFile(b.toString(), f);    
    }    
  }

  private void generateWebConfig(Set<String> rtl, boolean root) throws IOException {
    StringBuilder b = new StringBuilder();
    b.append(root ?  WC_START_ROOT : WC_START_OTHER);
    countTotal++;
    for (String rt : rtl) { 
      if (!Utilities.existsInList(rt, "v2", "v3") && !rt.contains(":")) {
        b.append("        <rule name=\""+rulePrefix()+rt+"\">\n" + 
            "          <match url=\"^("+rt+")/([A-Za-z0-9\\-\\.]{1,64})\" />\n" + 
            "          <action type=\"Rewrite\" url=\"cr"+rt.toLowerCase()+".asp?type={R:1}&amp;id={R:2}\" />\n" + 
            "        </rule>\n" + 
            "");
      }
    }
    b.append(WC_END);
    String wc = b.toString();
    File f = new File(Utilities.path(folder, "web.config"));
    if (f.exists()) {
      String wcc = FileUtilities.fileToString(f);
      if (wcc.equals(wc))
        return;
    }
    countUpdated++;
    FileUtilities.stringToFile(b.toString(),f);    
  }

  private String rulePrefix() {
    if (folder.equals(websiteRootFolder)) {
      throw new Error("This is wrong!");
    }
    String t = folder.substring(websiteRootFolder.length()+1);
    t = t.replace("/", ".").replace("\\", ".");
    return t+".";
  }

  private Set<String> listResourceTypes(Map<String, String> map) {
    Set<String> res = new HashSet<>();
    for (String s : map.keySet()) {
      if (s.contains("/")) {
        res.add(s.substring(0, s.indexOf("/")));
      }
    }
    return res;
  }

  public void buildOldAspRedirections() throws IOException {
    Map<String, String> map = createMap(false);
    if (map != null) {
      for (String s : map.keySet()) {
        if (!s.contains(":")) {
          String path = Utilities.path(folder, s, "index.asp");
          String p = s.replace("/", "-");
          String litPath = Utilities.path(folder, p)+".html";
          if (!new File(litPath+".xml").exists() && !new File(litPath+".json").exists()) 
            litPath = Utilities.path(folder, tail(map.get(s)));
          File file = new File(FileUtilities.changeFileExt(litPath, ".xml"));
          if (file.exists() && new File(FileUtilities.changeFileExt(litPath, ".json")).exists()) {
            createAspRedirect(path, map.get(s), Utilities.pathURL(vpath, head(file.getName())));
          }
        }
      }
    }
  }

  public void buildLitespeedRedirections() throws IOException {
    Map<String, String> map = createMap(false);
    if (map != null) {
      for (String s : map.keySet()) {
        File of = new File(Utilities.path(folder, s, "index.asp"));
        if (of.exists()) {
          of.delete();
        }
        String path = Utilities.path(folder, s, "index.php");
        String p = s.replace("/", "-");
        String litPath = Utilities.path(folder, p)+".html";
        if (!new File(litPath+".xml").exists() && !new File(litPath+".json").exists()) 
          litPath = Utilities.path(folder, tail(map.get(s)));
        File file = new File(FileUtilities.changeFileExt(litPath, ".xml"));
        if (file.exists() && new File(FileUtilities.changeFileExt(litPath, ".json")).exists()) {
          createLitespeedRedirect(path, map.get(s), Utilities.pathURL(vpath, head(file.getName())));
        }
      }
    }
  }

  private String head(String name) {
    return name.substring(0, name.indexOf("."));
  }

  private String tail(String name) {
    return name.substring(name.lastIndexOf("/")+1);
  }

  private void createLitespeedRedirect(String path, String urlHtml, String urlSrc) throws IOException {
    String t = PHP_TEMPLATE;
    t = t.replace("{{html}}", urlHtml);
    t = t.replace("{{literal}}", urlSrc);
    FileUtilities.createDirectory(FileUtilities.getDirectoryForFile(path));
    countTotal++;
    if (!new File(path).exists() || !FileUtilities.fileToString(path).equals(t)) {
      FileUtilities.stringToFile(t, path);
      countUpdated++;
    }
  }

  private void createAspRedirect(String path, String urlHtml, String urlSrc) throws IOException {
    String t = ASP_TEMPLATE;
    t = t.replace("{{html}}", urlHtml);
    t = t.replace("{{literal}}", urlSrc);
    FileUtilities.createDirectory(FileUtilities.getDirectoryForFile(path));
    countTotal++;
    if (!new File(path).exists() || !FileUtilities.fileToString(path).equals(t)) {
      FileUtilities.stringToFile(t, path);
      countUpdated++;
    }
  }

  private void createPhpRedirect(String path, String urlHtml, String urlSrc) throws IOException {
    String t = PHP_TEMPLATE;
    t = t.replace("{{html}}", urlHtml);
    t = t.replace("{{literal}}", urlSrc);
    FileUtilities.createDirectory(FileUtilities.getDirectoryForFile(path));
    countTotal++;
    if (!new File(path).exists() || !FileUtilities.fileToString(path).equals(t)) {
      FileUtilities.stringToFile(t, path);
      countUpdated++;
    }
  }
  
  private void createHtmlRedirect(String path, String urlHtml, String urlSrc) throws IOException {
    String t = HTML_TEMPLATE;
    t = t.replace("{{html}}", urlHtml);
    t = t.replace("{{literal}}", urlSrc);
    FileUtilities.createDirectory(FileUtilities.getDirectoryForFile(path));
    countTotal++;
    if (!new File(path).exists() || !FileUtilities.fileToString(path).equals(t)) {
      FileUtilities.stringToFile(t, path);
      countUpdated++;
    }
  }

  private Map<String, String> createMap(boolean isCore) throws IOException {
    if (new File(Utilities.path(folder, "spec.internals")).exists()) {
      JsonObject json = null;
      try {
        json = JsonParser.parseObjectFromFile(Utilities.path(folder, "spec.internals"));
      } catch (Exception e) {
        return null;
      }
      Map<String, String> res = parseSpecDetails(json);
      if (isCore) {
        scanAdditionalFiles(res);      
      }
      return res;
    }
    File f = new File(Utilities.path(folder, "package.tgz"));
    if (!f.exists())
      return null;
    pkg = NpmPackage.fromPackage(new FileInputStream(f));
    JsonObject json = null;
    try {
      json = JsonParser.parseObject(pkg.load("other", "spec.internals"));
    } catch (Exception e) {
      return null;
    }
    Map<String, String> res = parseSpecDetails(json);
    if (isCore) {
      scanAdditionalFiles(res);      
    }
    return res;
  }

  private void scanAdditionalFiles(Map<String, String> res) throws IOException {
    for (File f : new File(folder).listFiles()) {
      if (f.getName().endsWith(".json") && !f.getName().endsWith(".canonical.json") && new File(FileUtilities.changeFileExt(f.getAbsolutePath(), ".xml")).exists() && new File(FileUtilities.changeFileExt(f.getAbsolutePath(), ".html")).exists()) {
        JsonObject obj = JsonParser.parseObject(f);
        if (obj.has("resourceType") && obj.has("id")) {
          res.put(obj.asString("resourceType")+"/"+obj.asString("id"), Utilities.pathURL(vpath, FileUtilities.changeFileExt(f.getName(), ".html")));
        }
      }
    }
    
  }

  public Map<String, String> parseSpecDetails(JsonObject json) {
    Map<String, String> res = new HashMap<>();
    for (JsonProperty p : json.getJsonObject("paths").getProperties()) {
      String key = p.getName();
      if (key.contains("|")) {
        key = key.substring(0,  key.indexOf("|"));
      }
      if (key.length() >= canonical.length()+1 && key.startsWith(canonical)) {
        String value = p.getValue().asString();
        res.put(key.substring(canonical.length()+1), Utilities.pathURL(vpath, value));
      } else if (key.contains("/")) {
        res.put(key, Utilities.pathURL(vpath, p.getValue().asString()));        
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
