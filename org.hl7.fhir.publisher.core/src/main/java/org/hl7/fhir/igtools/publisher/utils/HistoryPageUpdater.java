package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map.Entry;

import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.JsonTrackingParser;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class HistoryPageUpdater {

  public static void main(String[] args) throws IOException {
    new HistoryPageUpdater().updateHistoryPages(args[0], args[1], args[1]);
  }

  public void updateHistoryPages(String source, String website, String folder) throws IOException {
    File d = new File(folder);
    if (new File(Utilities.path(d.getAbsolutePath(), "history.html")).exists() &&
        new File(Utilities.path(d.getAbsolutePath(), "package-list.json")).exists()) {
      updateHistoryPage(source, website, d.getAbsolutePath());
    }
    
    for (File f : d.listFiles()) {
      if (f.isDirectory()) {
        updateHistoryPages(source, website, f.getAbsolutePath());
      }
    }
  }

  public void updateHistoryPage(String sourceRepo, String rootFolder, String folder) throws IOException {
    System.out.println("Update history page at "+folder+" from "+sourceRepo);
    copyFiles(sourceRepo, folder);

    JsonObject json = JsonTrackingParser.parseJsonFile(Utilities.path(folder, "package-list.json"));
    scrubApostrophes(json);
    String jsonv = new GsonBuilder().create().toJson(json);

    String html = TextFile.fileToString(Utilities.path(sourceRepo, "history.template"));
    html = html.replace("$header$", loadTemplate(rootFolder, folder, "header.template"));
    html = html.replace("$preamble$", loadTemplate(rootFolder, folder, "preamble.template"));
    html = html.replace("$postamble$", loadTemplate(rootFolder, folder, "postamble.template"));
    html = fixParameter(html, "title", json.get("title").getAsString());
    html = fixParameter(html, "id", json.get("package-id").getAsString());
    html = fixParameter(html, "json", jsonv);
    File tgt = new File(Utilities.path(folder, "directory.html"));
    if (tgt.exists() && TextFile.fileToString(tgt).contains("<div id=\"history-data\"></div>")) {
      TextFile.stringToFile(html, Utilities.path(folder, "directory.html"), false);      
    } else {
      TextFile.stringToFile(html, Utilities.path(folder, "history.html"), false);
    }

    String index = new File(Utilities.path(folder, "index.html")).exists() ? TextFile.fileToString(Utilities.path(folder, "index.html")) : "XXXXX";
    if (index.contains("XXXX")) {
      html = TextFile.fileToString(Utilities.path(sourceRepo, "index.html"));
      html = fixParameter(html, "title", json.get("title").getAsString());
      html = fixParameter(html, "id", json.get("package-id").getAsString());
      html = fixParameter(html, "json", jsonv);
      TextFile.stringToFile(html, Utilities.path(folder, "index.html"), false);      
    }
  }

  private String fixParameter(String html, String name, String value) {
    while (html.contains("[%"+name+"%]")) {
      html = html.replace("[%"+name+"%]", value);
    }
    return html;
  }


  private String loadTemplate(String rootFolder, String folder, String filename) throws FileNotFoundException, IOException {
    while (new File(folder).exists()) {
      File f = new File(Utilities.path(folder, "templates", filename));
      if (f.exists()) {
        return TextFile.fileToString(f);
      }
      if (folder.equals(rootFolder)) {
        throw new Error("Not found: "+f.getAbsolutePath());
      }
      folder = Utilities.getDirectoryForFile(folder); 
    }
    return "";
  }

  private void copyFiles(String sourceRepo, String folder) throws IOException {
    IniFile ini = new IniFile(Utilities.path(sourceRepo, "manifest.ini"));
    if (ini.hasSection("files")) {
      for (String s : ini.getPropertyNames("files")) {
        File src = new File(Utilities.path(sourceRepo, s));
        File tgt = new File(Utilities.path(folder, s));
        if ("overwrite".equals(ini.getStringProperty("files", s)) || !tgt.exists()) {
          if (src.isDirectory()) {
            Utilities.createDirectory(tgt.getAbsolutePath());
            Utilities.copyDirectory(src.getAbsolutePath(), tgt.getAbsolutePath(), null);
          } else {
            Utilities.copyFile(src, tgt);
          }
        }
      }
    }
  }

  private void scrubApostrophes(JsonObject json) {
    for (Entry<String, JsonElement> p : json.entrySet()) {
      if (p.getValue().isJsonPrimitive()) {
        scrubApostrophesInProperty(p);
      } else if (p.getValue().isJsonObject()) {
        scrubApostrophes((JsonObject) p.getValue());
      } else if (p.getValue().isJsonArray()) {
        int i = 0;
        for (JsonElement ai : ((JsonArray) p.getValue())) {
          if (ai.isJsonPrimitive()) {
            if (ai.getAsString().contains("'"))
              throw new Error("Don't know how to handle apostrophes in arrays");
          } else if (ai.isJsonObject()) {
            scrubApostrophes((JsonObject) ai);
          } // no arrays containing arrays in package-list.json
          i++;
        }
      }
    }
  }

  private void scrubApostrophesInProperty(Entry<String, JsonElement> p) {
    String s = p.getValue().getAsString();
    if (s.contains("'")) {
      s = s.replace("'", "`");
      p.setValue(new JsonPrimitive(s));
    }
  }

}
