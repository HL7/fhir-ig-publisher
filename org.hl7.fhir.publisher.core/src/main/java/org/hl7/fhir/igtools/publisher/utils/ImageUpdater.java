package org.hl7.fhir.igtools.publisher.utils;

/*-
 * #%L
 * org.hl7.fhir.publisher.core
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

public class ImageUpdater {
  private String source;
  private String filename;

  public static void main(String[] args) throws FileNotFoundException, IOException {
    ImageUpdater self = new ImageUpdater();
    self.source = args[0];
    self.filename = args[2];
    self.process(new File(args[1]));
    System.out.println("Done");
  }

  private void process(File folder) throws FileNotFoundException, IOException {
    System.out.println("Folder: "+folder.getAbsolutePath());
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        process(f);
      }
      if (f.getName().equals(filename)) {
        System.out.println("  Overwrite "+f.getAbsolutePath()+" with "+source);
        IOUtils.copy(new FileInputStream(source), new FileOutputStream(f));
      }
    }
  }

}
