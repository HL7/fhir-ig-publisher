package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.hl7.fhir.utilities.FileUtilities;

public class HTMLRedirectScanner {

  public static void main(String[] args) throws FileNotFoundException, IOException {
    new HTMLRedirectScanner().scan(new File("C:\\web\\hl7.org\\fhir"));
    System.out.println("Done");
  }

  private void scan(File dir) throws FileNotFoundException, IOException {
    System.out.println("Scan "+dir.getAbsolutePath());
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        scan(f);
      } else if (f.getName().endsWith(".html")) {
        String src = FileUtilities.fileToString(f);
        if (src.contains("This page is a redirect to")) {
          System.out.println("Delete "+f.getAbsolutePath());
          f.delete();
        }
      }
    }
    
  }

  
  
}
