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


public class HL7OrgFhirFixer {

  public static void main(String[] args) throws IOException {
    File folder = new File("/Users/grahamegrieve/web/www.hl7.org.fhir");
    new HL7OrgFhirFixer().execute(folder.getAbsolutePath().length(), folder, true);
  }

  private void execute(int rootLen, File folder, boolean root) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        execute(rootLen, f, false);
      } else if (Utilities.existsInList(Utilities.getFileExtension(f.getName()), "json1", "json2", "xml1", "xml2")) {
        f.delete();
      } else if ("index.php".equals(f.getName())) { 
        //fixIndexPhp(f);   
      }
    }
  }

  private void fixIndexPhp(File f) throws FileNotFoundException, IOException {
    StringBuilder b = new StringBuilder();
    boolean del = false;
    for (String line : FileUtilities.fileToLines(f)) {
      if (del) {
        del = false;
      } else if (line.contains("'application/fhir+json'") || line.contains("'application/json+fhir'") || line.contains("'application/xml+fhir'")) {
        // omit  
        del = true;
      } else if (line.contains("'application/fhir+xml'")) {
        b.append(line.replace("application/fhir+xml", "xml")+"\r\n");
      } else if (line.contains("'json'")) {
        b.append(line.replace("elseif", "if")+"\r\n");
      } else {
        b.append(line.replace(".xml1'", ".xml'")+"\r\n");
      }
    }
    FileUtilities.stringToFile(b.toString(), f);
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
//        if (!isSpec(folder)) {
//          scanFolder(rootLen, f, list);
//        }
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