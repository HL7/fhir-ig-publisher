package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.parser.JsonParser;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;


public class FHIRWebSitePlainLanguageInjector {

  public static void main(String[] args) throws IOException {
    File folderRoot = new File(args[0]);
    new FHIRWebSitePlainLanguageInjector().execute(folderRoot, folderRoot, true);
  }

  private void execute(File rootFolder, File folder, boolean root) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        execute(rootFolder, f, false);
      } else if (!root && "package-list.json".equals(f.getName())) {
        processPackage(rootFolder, f);
      }
    }
  }

  private void processPackage(File rootFolder, File f) throws FileNotFoundException, IOException {
    System.out.println("Process "+f.getAbsolutePath());
    PackageList pl = new PackageList(JsonParser.parseObject(f));
    String id = pl.pid();
    String web = pl.canonical();
    if (pl.current() != null) {
      processRelease(id, web, pl.current(), new File(Utilities.path(FileUtilities.getDirectoryForFile(f), "index.html")));
    }
    for (PackageListEntry pv : pl.versions()) {
      String relPath = Utilities.getRelativeUrlPath(web, pv.path());
      if (relPath != null) {
        processRelease(id, web, pv, new File(Utilities.path(FileUtilities.getDirectoryForFile(f), relPath, "index.html")));
      }
    }
  }

  private void processRelease(String id, String web, PackageListEntry v, File f) throws FileNotFoundException, IOException {
    if (f.exists()) {
      String src = FileUtilities.fileToString(f);
      int i = src.indexOf("<!--EndReleaseHeader-->");
      int j = src.indexOf("<!--PlainLangHeader-->");
      if (i > 0 && j == -1) {
        i = i + "<!--EndReleaseHeader-->".length();
        String inj = "\r\n"+
           "  <!--PlainLangHeader--><div id=\"plain-lang-box\">Plain Language Summary Goes Here</div>"+
           "<script src=\"https://hl7.org/fhir/plain-lang.js\"></script>"+
           "<script type=\"application/javascript\" src=\"https://hl7.org/fhir/history-cm.js\">"+
           "</script><script>showPlainLanguage('"+id+"', '"+v.version()+"');</script><!--EndPlainLangHeader-->";
        src = src.substring(0, i)+inj+src.substring(i);
        FileUtilities.stringToFile(src, f);
      }
    }
    
  }
}

