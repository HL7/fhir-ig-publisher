package org.hl7.fhir.igtools.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hl7.fhir.utilities.FTPClient;
import org.hl7.fhir.utilities.SimpleHTTPClient;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.SimpleHTTPClient.HTTPResult;
import org.hl7.fhir.utilities.TextFile;

public class WebSourceProvider {

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
      HTTPResult res = fetcher.get(url);
      res.checkThrowException();
      TextFile.bytesToFile(res.getContent(), df);
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
      HTTPResult res = fetcher.get(url);
      res.checkThrowException();
      folderSources.put(path,  res.getContent());
      Utilities.unzip(new ByteArrayInputStream(res.getContent()), df.getAbsolutePath());
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

  public void cleanFolder(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));

    if (web) {      
      Path target = Path.of(df.getAbsolutePath());
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(folderSources.get(path)))) {
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
          Path newPath = Utilities.zipSlipProtect(zipEntry, target);
          Files.delete(newPath);
          zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
      }
      Utilities.deleteEmptyFolders(df);
    } else {
      // do nothing?
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
      HTTPResult res = fetcher.get(url);
      if (res.getCode() < 300 && res.getContent().length > 0) {
        TextFile.bytesToFile(res.getContent(), df);
      }
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
        b.append(Utilities.path(existingFilesBase, s)); 
        b.append("\r\n");
      }
      TextFile.stringToFile(b.toString(), deleteFileName());
      // for now, it must be done manually
      if (upload) {
        List<String> filesToUpload = Utilities.listAllFiles(destination, null);
        System.out.println("Ready to upload changes. "+filesToUpload.size()+" files to upload, "+existingFiles.size()+" files to delete");
        int t = filesToUpload.size()+existingFiles.size();
        System.out.println("Connect to "+uploadServer);
        System.out.print("Uploading.");
        FTPClient ftp = new FTPClient(uploadServer, uploadPath, uploadUser, uploadPword);
        ftp.connect();
        int c = 0;
        int p = 0;
        for (String s : existingFiles) {
          ftp.delete(Utilities.path(existingFilesBase, s));
          c++;
          p = progress(c, t, p);      
        }
        System.out.print("|");
        for (String s : filesToUpload) {
          ftp.upload(Utilities.path(destination, s), s);
          c++;
          p = progress(c, t, p);      
        }
        System.out.print(".");
      }
      System.out.println("!");
    } else {
      System.out.println("Applying changes to website source at "+source);
      for (String s : existingFiles) {
        new File(Utilities.path(s, existingFilesBase, s)).delete();
      }
      Utilities.copyDirectory(destination, source, null);
      System.out.println("  ... done");
    }
  }

  private int progress(int c, int t, int p) {
    int pc = (c * 100) / t;
    if (pc > p) {
      System.out.print(".");
    }
    return pc;    
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
}
