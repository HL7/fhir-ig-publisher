package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.hl7.fhir.utilities.FileUtilities;

public class JekyllRepatcher {

  public static void main(String[] args) throws FileNotFoundException, IOException {
    new JekyllRepatcher().process(new File(args[0]));
  }

  private void process(File folder) throws FileNotFoundException, IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        process(f);
      } else if (f.getName().endsWith(".html")) {
        String src = FileUtilities.fileToString(f);
        int i = src.indexOf("typeof=\"foaf:Document\">");
        if (i > -1) {
          String hdr = src.substring(0, i+"typeof=\"foaf:Document\">".length());
          int b = hdr.indexOf("<title>");
          int e = hdr.indexOf("</title>");
          String title = hdr.substring(b+7, e);
          
          src = src.substring(i+"typeof=\"foaf:Document\">".length());
          i = src.indexOf("</div>");
          src = src.substring(0, i).trim();
          
          src = "---\nlayout: page\ntitle: "+title+"\n---\n"+src.replace("\r\n", "\n");
          FileUtilities.stringToFile(src, f);
        }
      }
    }
    
  }

}
