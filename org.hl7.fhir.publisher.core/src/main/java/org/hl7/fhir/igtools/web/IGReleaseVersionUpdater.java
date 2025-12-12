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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class IGReleaseVersionUpdater {

  private int countTotal = 0;
  private int countUpdated = 0;

  private static final String START_HTML_MARKER = "<!--ReleaseHeader--><p id=\"publish-box\">";
  private static final String START_HTML_MARKER_MILESTONE = "<!--ReleaseHeader--><p id=\"publish-box-milestone\">";
  private static final String START_HTML_MARKER_MILESTONE_WS = "<!-- ReleaseHeader --><p id=\"publish-box\">";
  private static final String START_HTML_MARKER_PAST = "<!--ReleaseHeader--><p id=\"publish-box-past\">";
  private static final String START_HTML_MARKER_CURRENT = "<!--ReleaseHeader--><p id=\"publish-box-current\">";
  private static final String END_HTML_MARKER = "</p><!--EndReleaseHeader-->";
  private static final String END_HTML_MARKER_WS = "</p>  <!-- EndReleaseHeader -->";
  private static final String START_PUB_BOX = "<p id=\"publish-box\">";
  private static final String PUB_STYLE = "#publish-box";
  private static final String CSS = "#publish-box {  list-style: none;  padding: 0; }\np#publish-box { background-color: yellow; border:1px solid maroon; padding: 5px;}\nimg#publish-box { vertical-align: baseline; }\n#markdown-toc li{font-size: 1em;}\n";

  private String folder;
  private List<String> ignoreList;
  private JsonObject version;
  private List<String> ignoreListOuter;
  private String currentFolder;
  private int clonedCount;
  private int clonedTotal;
  private String rootUrl;
  private String rootFolder;


  public IGReleaseVersionUpdater(String folder, String rootUrl, String rootFolder, List<String> ignoreList, List<String> ignoreListOuter, JsonObject version, String currentFolder) {
    this.folder = folder;
    this.ignoreList = ignoreList;
    this.ignoreListOuter = ignoreListOuter;
    this.version = version;
    this.currentFolder = currentFolder;
    this.rootFolder = rootFolder;
    this.rootUrl = rootUrl;
  }

  public void updateStatement(String fragment, int level, List<PackageListEntry> milestones) throws FileNotFoundException, IOException {
    updateFiles(fragment, new File(folder), level, milestones);
  }

  private void updateFiles(String fragment, File dir, int level, List<PackageListEntry> milestones) throws FileNotFoundException, IOException {
    if (dir.exists()) {
      for (File f : dir.listFiles()) {
        if (ignoreList != null && ignoreList.contains(f.getAbsolutePath())) {
          continue;
        }
        if (ignoreListOuter != null && ignoreListOuter.contains(f.getAbsolutePath())) {
          continue;
        }
        if (Utilities.existsInList(f.getName(), "modeldoc", "quick", "qa.html", "qa-hta.html", "qa-txservers.html", "qa-dep.html", "qa.min.html", "history.html", "directory.html", "qa-tx.html", "qa-ipreview.html", "us-core-comparisons", "searchform.html")) {
          continue;
        }
        if (f.getName().startsWith("comparison-v")) {
          continue;
        }

        if (f.isDirectory() && !Utilities.existsInList(f.getName(), "html")) {
          updateFiles(fragment, f, level + 1, milestones);
        }

        if (f.getName().endsWith(".html") || f.getName().endsWith(".htm")) {
          String src = FileUtilities.fileToString(f);
          String srcl = src.toLowerCase();
          if (srcl.contains("http-equiv=\"refresh\"") || srcl.contains("<html><p>not generated in this build</p></html>")) {
            continue;
          }
          String o = src;
          int b = src.indexOf(START_HTML_MARKER);
          int l = START_HTML_MARKER.length();
          if (b == -1) {
            b = src.indexOf(START_HTML_MARKER_MILESTONE);
            l = START_HTML_MARKER_MILESTONE.length();
          }
          if (b == -1) {
            b = src.indexOf(START_HTML_MARKER_MILESTONE_WS);
            l = START_HTML_MARKER_MILESTONE_WS.length();
          }
          if (b == -1) {
            b = src.indexOf(START_HTML_MARKER_PAST);
            l = START_HTML_MARKER_PAST.length();
          }
          if (b == -1) {
            b = src.indexOf(START_HTML_MARKER_CURRENT);
            l = START_HTML_MARKER_CURRENT.length();
          }
          int e = src.indexOf(END_HTML_MARKER);
          if (e == -1) {
            e = src.indexOf(END_HTML_MARKER_WS);
          }
          if (b == -1 || e == -1) {
            System.out.println("no html insert in " + f.getAbsolutePath());
          }
          if (b > -1 && e == -1) {
            int i = b;
            while (src.charAt(i + 1) != '\n') i++;
            src = src.substring(0, i) + END_HTML_MARKER + src.substring(i);
            e = src.indexOf(END_HTML_MARKER);
          }
          if (b > -1 && e > -1) {
            String updatedFragment = fragment;
            if (updatedFragment.contains("{{fn}}")) {
              String rp = getRelativePath(f.getAbsolutePath());
              if (!folder.equals(currentFolder) && new File(Utilities.path(currentFolder, rp)).exists()) {
                updatedFragment = fragment.replace("{{fn}}", "/" + Utilities.pathURL(rp));
              } else {
                updatedFragment = fragment.replace("{{fn}}", "");
              }
            }
            src = src.substring(0, b + l) + fixForLevel(updatedFragment, level) + addPageVersions(f, milestones) + src.substring(e);
            if (!src.equals(o)) {
              FileUtilities.stringToFile(src, f);
              countUpdated++;
            }
            countTotal++;
          }
        }
      }
    } else {
      System.out.println("Unable to find directory "+dir.getAbsolutePath());
    }
  }

  private String addPageVersions(File f, List<PackageListEntry> milestones) throws IOException {
    if (milestones == null) {
      return "";
    }
    String relpath = FileUtilities.getRelativePath(folder, f.getAbsolutePath()); 
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(" ");
    int i = 0;
    for (PackageListEntry t : milestones) {
      String base = Utilities.path(rootFolder, FileUtilities.getRelativePath(rootUrl, t.path()));
      File fv = Utilities.pathFile(base, relpath);
      if (fv.exists()) {
        i++;
        if (t.version().equals(version.asString("version"))) {
          b.append("<b>"+Utilities.escapeXml(t.milestoneName())+"</b>");
        } else {
          String link = Utilities.pathURL(t.path(), relpath);
          b.append("<a data-no-external=\"true\" href=\""+link+"\">"+Utilities.escapeXml(t.milestoneName())+"</a>");
        }
      }
    }
    String result = ". Page versions: "+b.toString();
    return i == 0 ? "" : result;
  }

  private String getRelativePath(String absolutePath) {
    return absolutePath.substring(folder.length()+1);
  }

  private String fixForLevel(String fragment, int level) {
    String lvl = "../";
    String p = "";
    for (int i = 0; i < level; i++) {
      p = p + lvl;
    }
    return fragment.replace("{{path}}", p);
  }

  public int getCountTotal() {
    return countTotal;
  }

  public int getCountUpdated() {
    return countUpdated;
  }

  public void checkXmlJsonClones(String vf) throws IOException {
    clonedCount = 0;
    clonedTotal = 0;
    checkXmlJsonClones(new File(vf));
  }

  private void checkXmlJsonClones(File dir) throws IOException {
    if (ignoreList != null && ignoreList.contains(dir.getAbsolutePath())) {
      return;
    }
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        checkXmlJsonClones(f);
      } else if (f.getName().endsWith(".json")) {
        String src = FileUtilities.fileToString(f);
        if (src.contains("\"resourceType\"")) {
          clonedTotal++;
          checkUpdate(f, src, FileUtilities.changeFileExt(f.getAbsolutePath(), ".json1"));
          checkUpdate(f, src, FileUtilities.changeFileExt(f.getAbsolutePath(), ".json2"));
        } else {
          checkDeleteFile(FileUtilities.changeFileExt(f.getAbsolutePath(), ".json1"));
          checkDeleteFile(FileUtilities.changeFileExt(f.getAbsolutePath(), ".json2"));
        }
      } else if (f.getName().endsWith(".xml")) {
        clonedTotal++;
        String src = FileUtilities.fileToString(f);
        if (src.contains("xmlns=\"http://hl7.org/fhir\"")) {
          checkUpdate(f, src, FileUtilities.changeFileExt(f.getAbsolutePath(), ".xml1"));
          checkUpdate(f, src, FileUtilities.changeFileExt(f.getAbsolutePath(), ".xml2"));
        } else {
          checkDeleteFile(FileUtilities.changeFileExt(f.getAbsolutePath(), ".xml1"));
          checkDeleteFile(FileUtilities.changeFileExt(f.getAbsolutePath(), ".xml2"));          
        }
      }
    }
  }

  private void checkUpdate(File src, String cnt, String fn) throws FileNotFoundException, IOException {
    File dst = new File(fn);
    if (!dst.exists() || dst.lastModified() > dst.lastModified()) {
      clonedCount++;
      FileUtils.copyFile(src, dst);
    }
  }

  private void checkDeleteFile(String fn) {
    File f = new File(fn);
    if ((f.exists())) {
      clonedCount++;
      f.delete();
    }
  }

  public int getClonedCount() {
    return clonedCount;
  }

  public int getClonedTotal() {
    return clonedTotal;
  }


}
