package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.hl7.fhir.utilities.IniFile;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonProperty;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.json.parser.JsonParser;

public class HistoryPageUpdater {

  public static void main(String[] args) throws IOException {
    new HistoryPageUpdater().updateHistoryPages(args[0], args[1], args[2]);
  }

  public void updateHistoryPages(String source, String folder, String templateSrc) throws IOException {
    File d = new File(folder);
    if (new File(Utilities.path(d.getAbsolutePath(), "history.html")).exists() &&
        new File(Utilities.path(d.getAbsolutePath(), "package-list.json")).exists()) {
      updateHistoryPage(source, d.getAbsolutePath(), templateSrc, false);
    }
    
    for (File f : d.listFiles()) {
      if (f.isDirectory()) {
        updateHistoryPages(source, f.getAbsolutePath(), templateSrc);
      }
    }
  }

  public void updateHistoryPage(String sourceRepo, String folder, String templateSrc, boolean delta) throws IOException {
    System.out.println("Update history page at "+folder+" from "+sourceRepo+" and "+templateSrc);
    if (!delta) {
      copyFiles(sourceRepo, folder);
    } else {
      // throw new Error("not done yet");
    }

    JsonObject json = JsonParser.parseObjectFromFile(Utilities.path(folder, "package-list.json"));
    scrubApostrophes(json);
    String jsonv = JsonParser.compose(json, false).replace("\\t", "  ");

    String html = FileUtilities.fileToString(Utilities.path(sourceRepo, "history.template"));
    html = html.replace("$header$", loadTemplate(templateSrc, "header.template"));
    html = html.replace("$preamble$", loadTemplate(templateSrc, "preamble.template"));
    html = html.replace("$postamble$", loadTemplate(templateSrc, "postamble.template"));
    html = fixParameter(html, "title", json.asString("title"));
    html = fixParameter(html, "id", json.asString("package-id"));
    html = fixParameter(html, "json", jsonv);
    File tgt = new File(Utilities.path(folder, "directory.html"));
    if (tgt.exists() && FileUtilities.fileToString(tgt).contains("<div id=\"history-data\"></div>")) {
      FileUtilities.stringToFile(html, Utilities.path(folder, "directory.html"));      
    } else {
      FileUtilities.stringToFile(html, Utilities.path(folder, "history.html"));
    }

    if (delta) {
// don't want to do this ....      new File(Utilities.path(folder, "index.html")).delete();
    } else {
      String index = new File(Utilities.path(folder, "index.html")).exists() ? FileUtilities.fileToString(Utilities.path(folder, "index.html")) : "XXXXX";
      if (index.contains("XXXX")) {
        html = FileUtilities.fileToString(Utilities.path(sourceRepo, "index.html"));
        html = html.replace("XXXX", "");
        html = html.replace("$header$", loadTemplate(templateSrc, "header.template"));
        html = html.replace("$preamble$", loadTemplate(templateSrc, "preamble.template"));
        html = html.replace("$postamble$", loadTemplate(templateSrc, "postamble.template"));

        html = fixParameter(html, "title", json.asString("title"));
        html = fixParameter(html, "id", json.asString("package-id"));
        html = fixParameter(html, "json", jsonv);
        FileUtilities.stringToFile(html, Utilities.path(folder, "index.html"));      
      }
    }
  }

  private String fixParameter(String html, String name, String value) {
    while (html.contains("[%"+name+"%]")) {
      html = html.replace("[%"+name+"%]", value);
    }
    return html;
  }


  private String loadTemplate(String folder, String filename) throws FileNotFoundException, IOException {
    File f = new File(Utilities.path(folder, filename));
    if (f.exists()) {
      return FileUtilities.fileToString(f);
    } else {
      throw new Error("Not found: "+f.getAbsolutePath());
    }
  }

  private void copyFiles(String sourceRepo, String folder) throws IOException {
    IniFile ini = new IniFile(Utilities.path(sourceRepo, "manifest.ini"));
    if (ini.hasSection("files")) {
      for (String s : ini.getPropertyNames("files")) {
        File src = new File(Utilities.path(sourceRepo, s));
        File tgt = new File(Utilities.path(folder, s));
        if ("overwrite".equals(ini.getStringProperty("files", s)) || !tgt.exists()) {
          if (src.isDirectory()) {
            FileUtilities.createDirectory(tgt.getAbsolutePath());
            FileUtilities.copyDirectory(src.getAbsolutePath(), tgt.getAbsolutePath(), null);
          } else {
            FileUtilities.copyFile(src, tgt);
          }
        }
      }
    }
  }

  private void scrubApostrophes(JsonObject json) {
    for (JsonProperty p : json.getProperties()) {
      if (p.getValue().isJsonPrimitive()) {
        scrubApostrophesInProperty(p);
      } else if (p.getValue().isJsonObject()) {
        scrubApostrophes((JsonObject) p.getValue());
      } else if (p.getValue().isJsonArray()) {
        int i = 0;
        for (JsonElement ai : ((JsonArray) p.getValue())) {
          if (ai.isJsonPrimitive()) {
            if (ai.asString().contains("'"))
              throw new Error("Don't know how to handle apostrophes in arrays");
          } else if (ai.isJsonObject()) {
            scrubApostrophes((JsonObject) ai);
          } // no arrays containing arrays in package-list.json
          i++;
        }
      }
    }
  }

  private void scrubApostrophesInProperty(JsonProperty p) {
    String s = p.getValue().asString();
    if (s.contains("'")) {
      s = s.replace("'", "`");
      p.setValue(new JsonString(s));
    }
    if (s.contains("\"")) {
      s = s.replace("\"", "`");
      p.setValue(new JsonString(s));
    }
  }

}
