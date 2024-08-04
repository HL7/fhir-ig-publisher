package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class PackagePacker {

  public static void main(String[] args) throws FileNotFoundException, IOException {
   new PackagePacker().process("/Users/grahamegrieve/web/hl7.org/fhir/extensions/5.1.0-snapshot1/package-new");
  }

  private void process(String path) throws IOException {

    System.out.println("Build Package");
    NpmPackage npm = NpmPackage.fromFolder(path);
    npm.loadAllFiles();
    npm.save(new FileOutputStream("/Users/grahamegrieve/web/hl7.org/fhir/extensions/5.1.0-snapshot1/package.tgz"));
    
  }
}
