package org.hl7.fhir.igtools.renderers.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.hl7.fhir.utilities.FileUtilities;

public class LineLengthChecker {

  public static void main(String[] args) throws IOException {
    new LineLengthChecker().check(args[0], 8192);
  }

  private int total;
  
  private void check(String path, int limit) throws IOException {
    System.out.println("Checking line lengths in "+path);
    check(new File(path), limit);
    System.out.println("Finished - checked "+total+" files");
  }

  private void check(File file, int limit) throws IOException {
    for (File f : file.listFiles()) {
      if (f.isDirectory()) {
        check(f, limit);
      } else if (f.length() > 0) {
        total++;
        int length = 0;
        String source = FileUtilities.fileToString(f);
        for (String line : source.split("\\R")) {
          int l = line.length();
          if (l > length) {
            length = l;
          }
        }
        if (length > limit) {

          System.out.println("File "+f.getAbsolutePath()+": longest line = "+length);
        }
      }
    }
    
  }

}
