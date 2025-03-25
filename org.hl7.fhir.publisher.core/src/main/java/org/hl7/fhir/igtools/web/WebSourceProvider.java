package org.hl7.fhir.igtools.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hl7.fhir.utilities.CompressionUtilities;
import org.hl7.fhir.utilities.FTPClient;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.http.HTTPResult;
import org.hl7.fhir.utilities.http.ManagedWebAccess;

public class WebSourceProvider {

  public class UploadSorter implements Comparator<String> {

    @Override
    public int compare(String o1, String o2) {
      try {
        String f1;
        f1 = FileUtilities.getDirectoryForFile(o1);
        String f2 = FileUtilities.getDirectoryForFile(o2);
        if (f1 == null && f2 == null) {
          return o1.compareToIgnoreCase(o2);
        }
        if (f1 == null) {
          return 1;
        }
        if (f2 == null) {
          return -1;
        }
        if (f1.equals(f2)) {
          return o1.compareToIgnoreCase(o2);
        }
        return f2.compareToIgnoreCase(f1); // reversal is deliberate
      } catch (Exception e2) {
        return 0;
      }
    }
  }
  
  private String destination;
  private String source;
  private Map<String, byte[]> folderSources = new HashMap<>();

  public WebSourceProvider(String destination, String source) {
    super();
    this.destination = destination;
    this.source = source;
  }
  
  public void needFile(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));
    FileUtilities.createDirectory(FileUtilities.getDirectoryForFile(df.getAbsolutePath()));
    if (df.exists()) {
      df.delete();
    }
    File sf = new File(Utilities.path(source, path));
    if (!sf.exists()) {
      throw new Error("Error: Attempt to copy "+sf.getAbsolutePath()+" but it doesn't exist");
    }
    FileUtilities.copyFile(sf, df);
  }

  public void needFolder(String path, boolean clear) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists() && !df.isDirectory()) {
      df.delete();
    }
    if (!df.exists()) {
      FileUtilities.createDirectory(df.getAbsolutePath());
    } else if (clear) {
      FileUtilities.clearDirectory(df.getAbsolutePath());
    }
    
    File sf = new File(Utilities.path(source, path));
    if (!sf.exists()) {
      throw new Error("Error: Attempt to copy from "+sf.getAbsolutePath()+" but it doesn't exist");
    }
    if (!sf.isDirectory()) {
      throw new Error("Error: Attempt to copy from "+sf.getAbsolutePath()+" but it isn't a folder");
    }
    FileUtilities.copyDirectory(sf.getAbsolutePath(), df.getAbsolutePath(), null);
    
  }

  private String stats(long length, long t) {
    long millis = System.currentTimeMillis()-t;
    if (millis == 0) {
      return Utilities.describeSize(length)+", "+Utilities.describeDuration(Duration.ofMillis(millis));
    } else {
      return Utilities.describeSize(length)+", "+Utilities.describeDuration(Duration.ofMillis(millis))+", "+length/millis+"kb/sec";
    }
  }

  public void cleanFolder(String path) throws IOException {
    File df = new File(path == null ? destination : Utilities.path(destination, path));

    // do nothing?
  }

  protected static void cleanZipTargets(Path target, InputStream inputStream) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(inputStream)) {
      ZipEntry zipEntry = zis.getNextEntry();
      while (zipEntry != null) {
        Path newPath = CompressionUtilities.zipSlipProtect(CompressionUtilities.makeOSSafe(zipEntry.getName()), target);
        if (Files.exists(newPath)) {
          Files.delete(newPath);
        }
        zipEntry = zis.getNextEntry();
      }
      zis.closeEntry();
    }
  }

  public void needOptionalFolder(String path, boolean clear) throws IOException {
    File sf = new File(Utilities.path(source, path));
    if (sf.exists()) {
      needFolder(path, false);
    }    
  }

  public void needOptionalFile(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists()) {
      df.delete();
    }

    File sf = new File(Utilities.path(source, path));
    if (sf.exists()) {
      FileUtilities.copyFile(sf, df);
    }    
  }

  public void finish(String existingFilesBase, List<String> existingFiles) throws IOException {
    System.out.println("Applying changes to website source at "+source);
    if (existingFiles != null) {
      for (String s : existingFiles) {
        new File(Utilities.path(s, existingFilesBase, s)).delete();
      }
    }
    FileUtilities.copyDirectory(destination, source, null);
    System.out.println("  ... done");
  }

  private String deleteFileName() throws IOException {
    return Utilities.path("[tmp]", "files-to-delete.txt");
  }

  public String instructions(int fc) throws IOException {
    return "The web site source in "+source+" has been updated";
  }

  public String verb() {
    return "Copy";
  }
}
