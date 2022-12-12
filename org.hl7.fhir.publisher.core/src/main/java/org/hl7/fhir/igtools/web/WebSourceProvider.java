package org.hl7.fhir.igtools.web;

import java.io.File;
import java.io.IOException;

import org.hl7.fhir.utilities.Utilities;

public class WebSourceProvider {

  private String destination;
  private String source;
  private boolean web;

  public WebSourceProvider(String destination, String source) {
    super();
    this.destination = destination;
    this.source = source;
  }

  public void needFile(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists()) {
      df.delete();
    }
    if (web) {
      throw new Error("Not done yet");
    } else {
      File sf = new File(Utilities.path(source, path));
      if (!sf.exists()) {
        throw new Error("Error: Attempt to copy "+sf.getAbsolutePath()+" but it doesn't exist");
      }
      Utilities.copyFile(sf, df);
    }
  }

  public void needFolder(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists() && !df.isDirectory()) {
      df.delete();
    }
    if (df.exists()) {
      Utilities.clearDirectory(df.getAbsolutePath());
    } else {
      Utilities.createDirectory(df.getAbsolutePath());
    }
    if (web) {
      throw new Error("Not done yet");
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

  public void needOptionalFile(String path) throws IOException {
    File df = new File(Utilities.path(destination, path));
    if (df.exists()) {
      df.delete();
    }
    if (web) {
      throw new Error("Not done yet");
    } else {
      File sf = new File(Utilities.path(source, path));
      if (sf.exists()) {
        Utilities.copyFile(sf, df);
      }
    }
  }
}
