package org.hl7.fhir.igtools.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hl7.fhir.utilities.FTPClient;
import org.hl7.fhir.utilities.SimpleHTTPClient;
import org.hl7.fhir.utilities.SimpleHTTPClient.HTTPResult;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;

public class WebSourceProvider {

  public class UploadSorter implements Comparator<String> {

    @Override
    public int compare(String o1, String o2) {
      String f1 = Utilities.getDirectoryForFile(o1);
      String f2 = Utilities.getDirectoryForFile(o2);
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
    }

  }

  private String destination;
  private String source;
  private boolean web;
  private Map<String, byte[]> folderSources = new HashMap<>();
  private boolean upload = false;
  private String uploadServer;
  private String uploadPath;
  private String uploadUser;
  private String uploadPword;

  public WebSourceProvider(String destination, String source) {
    super();
    this.destination = destination;
    this.source = source;
    web = Utilities.startsWithInList(source, "http://", "https://");
  }

  public void configureUpload(String server, String path, String user, String pword) {
    upload = true;
    uploadServer = server;
    uploadPath = path;
    uploadUser = user;
    uploadPword = pword;
  }
  
  public void needFile(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists()) {
      df.delete();
    }
    if (web) {
      SimpleHTTPClient fetcher = new SimpleHTTPClient();
      String url = Utilities.pathURL(source, path)+"?nocache=" + System.currentTimeMillis();
      System.out.println("Fetch "+ url);
      long t = System.currentTimeMillis();
      HTTPResult res = fetcher.get(url);
      res.checkThrowException();
      TextFile.bytesToFile(res.getContent(), df);
      System.out.println("  ... done ("+stats(res.getContent().length, t)+")");
    } else {
      File sf = new File(Utilities.path(source, path));
      if (!sf.exists()) {
        throw new Error("Error: Attempt to copy "+sf.getAbsolutePath()+" but it doesn't exist");
      }
      Utilities.copyFile(sf, df);
    }
  }

  public void needFolder(String path, boolean clear) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists() && !df.isDirectory()) {
      df.delete();
    }
    if (!df.exists()) {
      Utilities.createDirectory(df.getAbsolutePath());
    } else if (clear) {
      Utilities.clearDirectory(df.getAbsolutePath());
    }
    if (web) {
      SimpleHTTPClient fetcher = new SimpleHTTPClient();
      String url = Utilities.pathURL(source, path, "_ig-pub-archive.zip")+"?nocache=" + System.currentTimeMillis();
      System.out.println("Fetch "+ url);
      long t = System.currentTimeMillis();
      HTTPResult res = fetcher.get(url);
      res.checkThrowException();
      folderSources.put(path, res.getContent());
      Utilities.unzip(new ByteArrayInputStream(res.getContent()), df.getAbsolutePath());
      System.out.println("  ... done ("+stats(res.getContent().length, t)+")");
    } else {
      File sf = new File(Utilities.path(source, path));
      if (!sf.exists()) {
        throw new Error("Error: Attempt to copy from "+sf.getAbsolutePath()+" but it doesn't exist");
      }
      if (!sf.isDirectory()) {
        throw new Error("Error: Attempt to copy from "+sf.getAbsolutePath()+" but it isn't a folder");
      }
      Utilities.copyDirectory(sf.getAbsolutePath(), df.getAbsolutePath(), null);
    }
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

    if (web) {      
      Path target = Path.of(df.getAbsolutePath());
      byte[] buf = folderSources.get(path);
      if (buf != null) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buf);
        cleanZipTargets(target, byteArrayInputStream);
        Utilities.deleteEmptyFolders(df);
      }
    } else {
      // do nothing?
    }
  }

  protected static void cleanZipTargets(Path target, InputStream inputStream) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(inputStream)) {
      ZipEntry zipEntry = zis.getNextEntry();
      while (zipEntry != null) {
        Path newPath = Utilities.zipSlipProtect(Utilities.makeOSSafe(zipEntry.getName()), target);
        if (Files.exists(newPath)) {
          Files.delete(newPath);
        }
        zipEntry = zis.getNextEntry();
      }
      zis.closeEntry();
    }
  }

  public void needOptionalFile(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists()) {
      df.delete();
    }
    if (web) {
      SimpleHTTPClient fetcher = new SimpleHTTPClient();
      String url = Utilities.pathURL(source, path)+"?nocache=" + System.currentTimeMillis();
      System.out.println("Fetch "+ url);
      long t = System.currentTimeMillis();
      HTTPResult res = fetcher.get(url);
      if (res.getCode() < 300 && res.getContent().length > 0) {
        TextFile.bytesToFile(res.getContent(), df);
      }
      System.out.println("  ... done ("+stats(res.getContent().length, t)+")");
    } else {
      File sf = new File(Utilities.path(source, path));
      if (sf.exists()) {
        Utilities.copyFile(sf, df);
      }
    }
  }

  public void finish(String existingFilesBase, List<String> existingFiles) throws IOException {
    if (web) {
      StringBuilder b = new StringBuilder();
      for (String s : existingFiles) {
        b.append(existingFilesBase == null ? s : Utilities.path(existingFilesBase, s)); 
        b.append("\r\n");
      }
      TextFile.stringToFile(b.toString(), deleteFileName());
      // for now, it must be done manually
      if (upload) {
        List<String> filesToUpload = Utilities.listAllFiles(destination, null);
        Collections.sort(filesToUpload, new UploadSorter()); // more specific files first
        System.out.println("Ready to upload changes. "+filesToUpload.size()+" files to upload, "+existingFiles.size()+" files to delete");
        int t = filesToUpload.size()+existingFiles.size();
        System.out.println("Connect to "+uploadServer);
        FTPClient ftp = new FTPClient(uploadServer, uploadPath, uploadUser, uploadPword);
        ftp.connect();
        int lineLength = 0;
        int count = 0;
        long start = System.currentTimeMillis();
        if (!existingFiles.isEmpty()) {
          System.out.print("Deleting");
          for (String s : existingFiles) {
            count++;
            lineLength = doProgressNote(s, count, start, existingFiles.size(), lineLength);
            ftp.delete(existingFilesBase == null ? s : Utilities.path(existingFilesBase, s));
          }
        }
        System.out.println("Uploading");
        int failCount = 0;
        count = 0;
        start = System.currentTimeMillis();
        lineLength = 0;
        for (String s : filesToUpload) {
          count++;
          lineLength = doProgressNote(s, count, start, filesToUpload.size(), lineLength);
                    
          if (!s.contains(":")) { // hack around a bug that should be fixed elsewhere
            try {
              String fn = Utilities.path(destination, s);
              ftp.upload(fn, s);
              failCount = 0;
            } catch (Exception e) {
              System.out.println("");
              System.out.println("Error uploading file '"+s+"': "+e.getMessage()+". Trying again");
              try {
                ftp.upload(Utilities.path(destination, s), s);
                failCount = 0;
              } catch (Exception e2) {
                try {
                  System.out.println("Reconnecting after second error: "+e.getMessage());
                  ftp = new FTPClient(uploadServer, uploadPath, uploadUser, uploadPword);
                  ftp.connect();
                  ftp.upload(Utilities.path(destination, s), s);
                } catch (Exception e3) {
                  failCount++;
                  System.out.println("");
                  System.out.println("Error uploading file '"+s+"': "+e2.getMessage());
                  System.out.println("Need to manually copy '"+Utilities.path(destination, s)+"' to '"+s);
                  if (failCount >= 10) {
                    throw new Error("Too many sequential errors copying files (10). Stopping.");
                  }
                }
              }
            }
          }   
        }
      }
      System.out.println("");
      System.out.println("Upload finished");
    } else {
      System.out.println("Applying changes to website source at "+source);
      for (String s : existingFiles) {
        new File(Utilities.path(s, existingFilesBase, s)).delete();
      }
      Utilities.copyDirectory(destination, source, null);
      System.out.println("  ... done");
    }
  }

  private int doProgressNote(String file, int count, long start, int size, int length) {
    System.out.print(Utilities.padLeft("", '\b', length));
    String note;
    
    int pct = ((count * 50) / size);
    String pc = "["+Utilities.padRight(Utilities.padLeft("",  '#', pct), ' ', 50)+"]";
    long secondsDone = (System.currentTimeMillis() - start) / 1000;
    if (count == 0 || secondsDone == 0) {
      note = pc;
    } else {
      float rate = (count * 60) / secondsDone;    
      long secondsToGo = ((secondsDone * size) / count) - secondsDone;
      Duration d = Duration.ofSeconds(secondsToGo);
      note = pc+ "("+rate+" files/min, ~"+Utilities.describeDuration(d)+" to go) "+Utilities.getDirectoryForFile(file)+"         ";
    }
    System.out.print(note);
    System.out.flush();
    return note.length();
  }

  private String deleteFileName() throws IOException {
    return Utilities.path("[tmp]", "files-to-delete.txt");
  }

  public String instructions(int fc) throws IOException {
    if (web) {
      if (upload) {
        return "The web site source at ftp://"+uploadServer+"/"+uploadPath+" has been updated";
      } else {
        if (fc > 0) {
          return "Upload all the files in "+destination+" to "+source+" using your preferred file copy method. Note that there is "+fc+" files to be deleted on the website (see "+deleteFileName()+")"; 

        } else {
          return "Upload all the files in "+destination+" to "+source+" using your preferred file copy method"; 
        }
      }
    } else {
      return "The web site source in "+source+" has been updated";
    }
  }

  public boolean isWeb() {
    return web;
  }

  public String verb() {
    return web ? "Upload" : "Copy";
  }
}
