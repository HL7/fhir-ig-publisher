package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.ZipGenerator;

/**
 * A standard download for FHIR has the following properties
 * 
 *  It includes all the html pages
 *  it includes all the images / css / etc 
 *  it doesn't include zips, jars, tgz - they are left in place, and references to them redirected to the source 
 *  any reference to history.html (or directory.html for core)
 *  All the files are pushed down to \site
 *  a single index.html which redirects to site\index.html is included
 *     
 * @author graha
 *
 */
public class DownloadBuilder {

  private static final String[] DO_NOT_DOWNLOAD = {".zip", ".tgz", ".xml1", ".xml2", ".json1", ".json2", ".bck", ".bak", ".bcknpm", ".pack", ".asp", ".cfml"};
  private static final String[] EXEMPT_FILES = {"web.config", "history.template", "registry", "validator"};
  private static final String CURRENT_VERSION = "1";

  public static void main(String[] args) throws IOException {
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2011Aug", "http://hl7.org/fhir", "http://hl7.org/fhir/2011Aug", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2012May", "http://hl7.org/fhir", "http://hl7.org/fhir/2012May", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2012Sep", "http://hl7.org/fhir", "http://hl7.org/fhir/2012Sep", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2013Jan", "http://hl7.org/fhir", "http://hl7.org/fhir/2013Jan", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2013Sep", "http://hl7.org/fhir", "http://hl7.org/fhir/2013Sep", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2015Dec", "http://hl7.org/fhir", "http://hl7.org/fhir/2015Dec", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2015Jan", "http://hl7.org/fhir", "http://hl7.org/fhir/2015Jan", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2015May", "http://hl7.org/fhir", "http://hl7.org/fhir/2015May", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2015Sep", "http://hl7.org/fhir", "http://hl7.org/fhir/2015Sep", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2016Jan", "http://hl7.org/fhir", "http://hl7.org/fhir/2016Jan", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2016May", "http://hl7.org/fhir", "http://hl7.org/fhir/2016May", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2016Sep", "http://hl7.org/fhir", "http://hl7.org/fhir/2016Sep", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2017Jan", "http://hl7.org/fhir", "http://hl7.org/fhir/2017Jan", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2018Dec", "http://hl7.org/fhir", "http://hl7.org/fhir/2018Dec", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2018Jan", "http://hl7.org/fhir", "http://hl7.org/fhir/2018Jan", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2018May", "http://hl7.org/fhir", "http://hl7.org/fhir/2018May", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\2018Sep", "http://hl7.org/fhir", "http://hl7.org/fhir/2018Sep", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\DSTU1", "http://hl7.org/fhir", "http://hl7.org/fhir/DSTU1", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\DSTU2", "http://hl7.org/fhir", "http://hl7.org/fhir/DSTU2", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\STU3", "http://hl7.org/fhir", "http://hl7.org/fhir/STU3", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir\\R4", "http://hl7.org/fhir", "http://hl7.org/fhir/R4", null).execute();
    new DownloadBuilder("C:\\web\\hl7.org\\fhir", "http://hl7.org/fhir", "http://hl7.org/fhir", null).execute();
  }

  private String srcFolder;
  private String canonical;
  private String url;
  private File checkFile;
  private ZipGenerator zip;
  private int counter;
  private String zipfilename;
  private List<String> ignoreList;
  
  public DownloadBuilder(String srcFolder, String canonical, String url, List<String> ignoreList) {
    super();
    this.srcFolder = srcFolder;
    this.canonical = canonical;
    this.url = url;
    this.ignoreList = ignoreList;
  }

  private boolean isCore() {
    return "http://hl7.org/fhir".equals(canonical);
  }
  
  private String extension(File f) {
    return extension(f.getAbsolutePath());
  }
  
  private String extension(String s) {
    if (!s.contains("."))
      return ".";
    return s.substring(s.lastIndexOf("."));
  }
  
  private boolean check() throws IOException {
    zipfilename = isCore() ? "fhir-spec" : "full-ig";
    File z = new File(Utilities.path(srcFolder, zipfilename+".zip"));
    if (!z.exists())
      return false;
    return z.length() > 2000;
  }

  private void finish() throws IOException {
    zip.close();  
  }

  private void start() throws FileNotFoundException, IOException {
    zip = new ZipGenerator(Utilities.path(srcFolder, zipfilename+".zip"));
    zip.addBytes("index.html", makeRedirect(), false);
  }

  private byte[] makeRedirect() throws IOException {
    String html = "<html>\n" + 
        "<head>\n" + 
        "<meta http-equiv=\"Refresh\" content=\"0; url=site/index.html\" />\n" + 
        "</head>\n" + 
        "<body>\n" + 
        "<p>See here: <a href=\"site/index.html\">this link</a>.</p>\n" + 
        "</body>\n" + 
        "</html>\n" + 
        "";
    return FileUtilities.stringToBytes(html);
  }

  private void addBytes(String absolutePath, byte[] content) throws IOException {
    String path = "site"+absolutePath.substring(srcFolder.length());
    zip.addBytes(path, content, false);
  }
  
  public void execute() throws IOException {
    if (!check()) {
      System.out.println("Building download for "+srcFolder+" url = "+url+", canonical = "+canonical);
      System.out.print("  ");
      counter = 0;
      start();
      scan(new File(srcFolder), 0);
      System.out.println();
      finish();
      System.out.println(" ..Finished");
    }
  }

  private void scan(File folder, int level) throws FileNotFoundException, IOException {
    for (File f : folder.listFiles()) {
      if (!Utilities.existsInList(f.getName(), EXEMPT_FILES)) {
        if (f.isDirectory()) {
          boolean ok = ignoreList == null || !ignoreList.contains(f.getAbsolutePath());
          File pck = new File(Utilities.path(f.getAbsolutePath(), "full-ig.zip"));
          if (pck.exists())
            ok = false;
          pck = new File(Utilities.path(f.getAbsolutePath(), "fhir-spec.zip"));
          if (pck.exists())
            ok = false;
          pck = new File(Utilities.path(f.getAbsolutePath(), "package-list.json"));
          if (pck.exists())
            ok = false;
          if (ok) {
            scan(f, level+1);
          }
        } else {
          String ext = extension(f);
          if (".html".equals(ext) || ".htm".equals(ext)) {
            if (counter % 10 == 0) {
              System.out.print(".");
            }
            counter++;
            if (counter == 800) {
              System.out.println();
              counter = 0;
              System.out.print("  ");
            }
            String html = FileUtilities.fileToString(f);
            html = fixLinks(html, level);
            addBytes(f.getAbsolutePath(), FileUtilities.stringToBytes(html));
          } else if (!Utilities.existsInList(ext, DO_NOT_DOWNLOAD)) {
            addBytes(f.getAbsolutePath(), FileUtilities.fileToBytes(f));
          }
        }
      }
    }
  }

  private String fixLinks(String html, int level) {
    html = html.replace("This is the current published version. ", "");
    html = html.replace("This is the current published version in its permanent home (it will always be available at this URL).", "");
    html = html.replace("<p id=\"publish-box\">", "<p id=\"publish-box\"> <b>This page is part of a downloaded copy of this specification.</b> ");
    
    int cursor = 0;
    while (cursor < html.length()-7) {
      if (html.substring(cursor, cursor+6).startsWith("href=\"")) {
        int start = cursor + 6;
        int end = start + 1;
        while (html.charAt(end) != '"') {
          end++;
        }
        String link = html.substring(start, end);
        if (!link.startsWith("http:") && !link.startsWith("http:") && Utilities.existsInList(extension(link), DO_NOT_DOWNLOAD)) {
          if (level > 0) {
            String s = "";
            for (int i = 0; i < level; i++) {
              s = s + "../";
            }
            if (link.startsWith(s)) {
              link = Utilities.pathURL(url, link.substring(s.length()));
            }
            html = html.substring(0, start)+link+html.substring(end);  
          } else
            html = html.substring(0, start)+Utilities.pathURL(url, link)+html.substring(end);  
        }  
        cursor = end;
      } else {
        cursor++;
      }
    }
    return html;
  }




}
