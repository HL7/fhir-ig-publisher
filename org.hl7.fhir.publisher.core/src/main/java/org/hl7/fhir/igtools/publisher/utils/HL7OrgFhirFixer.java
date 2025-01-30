package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;


public class HL7OrgFhirFixer {

  public static void main(String[] args) throws IOException {
    File folder = new File("/Users/grahamegrieve/web/www.hl7.org.fhir");
    new HL7OrgFhirFixer().execute(folder.getAbsolutePath().length(), folder, true);
  }

  private void execute(int rootLen, File folder, boolean root) throws IOException {
    boolean isSpec = isSpec(folder);
    if (isSpec) {
      System.out.print("Found Spec at "+folder.getAbsolutePath());
      processSpec(rootLen, folder);
    }    
    boolean isEmpty = true;
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        isEmpty = false;
        execute(rootLen, f, false);
      } else if (f.getName().endsWith(".asp")) {
        f.delete();
      } else {
        isEmpty = false;
      }
    }
    if (isEmpty) {
      folder.delete();
    }
  }

  private boolean isSpec(File folder) throws IOException {
    if (new File(Utilities.path(folder.getAbsolutePath(), "package.tgz")).exists()) {
      return true;
    }
    if (new File(Utilities.path(folder.getAbsolutePath(), "patient.html")).exists()) {
      return true;
    }
    if (new File(Utilities.path(folder.getAbsolutePath(), "patient.htm")).exists()) {
      return true;
    }
    return false;
  }

  private void processSpec(int rootLen, File folder) throws IOException {
    Map<String, Map<String, String>> list = new HashMap<>();
    
    scanFolder(rootLen, folder, list);

    int size = 0;
    for (String t : list.keySet()) {
      size += list.get(t).size();
      String tf = Utilities.noString(t) ? folder.getAbsolutePath() : Utilities.path(folder.getAbsolutePath(), t);
      Utilities.createDirectoryNC(tf);
      Map<String, String> map = list.get(t);
      for (String id : map.keySet()) {
        String idf = Utilities.path(tf, id);
        Utilities.createDirectoryNC(idf);
        String rf = Utilities.path(idf, "index.php");
        TextFile.stringToFile(genRedirect(map.get(id)), rf);
      }
    }
    System.out.println(" "+size+" resources");
  }

  private String genRedirect(String url) {
    String ub = url.replace(".json", "");
    return "<?php\r\n"+
"function Redirect($url)\r\n"+
"{\r\n"+
"  header('Location: ' . $url, true, 302);\r\n"+
"  exit();\r\n"+
"}\r\n"+
"\r\n"+
"$accept = $_SERVER['HTTP_ACCEPT'];\r\n"+
"if (strpos($accept, 'application/json+fhir') !== false)\r\n"+
"  Redirect('/fhir"+ub+".json2');\r\n"+
"elseif (strpos($accept, 'application/fhir+json') !== false)\r\n"+
"  Redirect('/fhir"+ub+".json1');\r\n"+
"elseif (strpos($accept, 'json') !== false)\r\n"+
"  Redirect('/fhir"+ub+".json');\r\n"+
"elseif (strpos($accept, 'application/xml+fhir') !== false)\r\n"+
"  Redirect('/fhir"+ub+".xml2');\r\n"+
"elseif (strpos($accept, 'application/fhir+xml') !== false)\r\n"+
"  Redirect('/fhir"+ub+".xml1');\r\n"+
"elseif (strpos($accept, 'html') !== false)\r\n"+
"  Redirect('/fhir"+ub+".html');\r\n"+
"else \r\n"+
"  Redirect('/fhir"+ub+".json');\r\n"+
"?>\r\n"+
"\r\n"+
"You should not be seeing this page. If you do, PHP has failed badly.\r\n"+
"\r\n";
  }

  private void scanFolder(int rootLen, File folder, Map<String, Map<String, String>> list) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.getName().equals("web.config")) {
        f.delete();
      } else if (f.getName().endsWith(".asp")) {
        f.delete();
      } else if (f.isDirectory()) {
        if (!isSpec(folder)) {
          scanFolder(rootLen, f, list);
        }
      } else if (f.getName().endsWith(".json") && !f.getName().contains(".canonical.")) {
        try {
          JsonElement je = JsonParser.parse(f);
          if (je.isJsonObject()) {
            JsonObject j = je.asJsonObject();
            if (j.has("resourceType") && j.has("id")) {
              String rt = j.asString("resourceType");
              String id = j.asString("id");
              String link = f.getAbsolutePath().substring(rootLen);
              Map<String, String> ft = makeMapForType(list, rt);
              ft.put(id, link);
              if (j.has("url")) {
                String url = j.asString("url");
                if (url.startsWith("http://hl7.org/fhir/")) {
                  String tail = url.substring(20);
                  if (j.has("version")) {
                    ft.put(id+"|"+j.asString("version"), link);
                  }
                  if (!tail.contains("/") && !tail.contains(".")) {
                    ft = makeMapForType(list, "");
                    ft.put(tail, link);
                    if (j.has("version")) {
                      ft.put(tail+"|"+j.asString("version"), link);
                    }
                  }
                }
              }
            }
          }
        } catch (Exception e) {
        }
      }
    }
  }

  public Map<String, String> makeMapForType(Map<String, Map<String, String>> list, String rt) {
    Map<String, String> ft = list.get(rt);
    if (ft == null) {
      ft = new HashMap<String, String>();
      list.put(rt, ft);
    }
    return ft;
  }
}