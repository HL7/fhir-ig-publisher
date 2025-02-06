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
import java.io.IOException;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;

public class IGRedirectorGenerator {

  public static void main(String[] args) throws IOException {
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2017Apr", "C:\\web\\org.hl7.au\\base2017Apr", "../base/2017Apr");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2017Dec", "C:\\web\\org.hl7.au\\base2017Dec", "../base/2017Dec");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2017Jul", "C:\\web\\org.hl7.au\\base2017Jul", "../base/2017Jul");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2017Sep", "C:\\web\\org.hl7.au\\base2017Sep", "../base/2017Sep");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2018Mar", "C:\\web\\org.hl7.au\\base2018Mar", "../base/2018Mar");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2018Oct", "C:\\web\\org.hl7.au\\base2018Oct", "../base/2018Oct");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2018Sep", "C:\\web\\org.hl7.au\\base2018Sep", "../base/2018Sep");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\base\\2019Feb", "C:\\web\\org.hl7.au\\base2019Feb", "../base/2019Feb");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\pd\\2017Dec", "C:\\web\\org.hl7.au\\pd2017Dec", "../pd/2017Dec");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\pd\\2017Jul", "C:\\web\\org.hl7.au\\pd2017Jul", "../pd/2017Jul");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\pd\\2017Sep", "C:\\web\\org.hl7.au\\pd2017Sep", "../pd/2017Sep");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\pd\\2018Mar", "C:\\web\\org.hl7.au\\pd2018Mar", "../pd/2018Mar");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\pd\\2018Oct", "C:\\web\\org.hl7.au\\pd2018Oct", "../pd/2018Oct");
    new IGRedirectorGenerator().generateRedirects("C:\\web\\org.hl7.au\\pd\\2019Feb", "C:\\web\\org.hl7.au\\pd2019Feb", "../pd/2019Feb");


  }

  private int total;

  /**
   * 
   * @param src - the source of the IG to generate redirects for (local folder) 
   * @param dest - the destination in which to create the redirects (local folder)
   * @param relativeLocation - the relative path from src to dest - e.g. ../other/something  (yes, it could often be inferred, but not always) Note, this is an HTML path, not a local path 
   * @throws IOException 
   */
  public void generateRedirects(String src, String dest, String relativeLocation) throws IOException {
    FileUtilities.clearDirectory(dest);
    total = 0;
    processFiles(new File(src), new File(dest), relativeLocation);
    System.out.println("Created "+total+" redirects for "+src+" in "+dest);
  }

  private void processFiles(File src, File dest, String relativeLocation) throws IOException {
    for (File f : src.listFiles()) {
      if (f.isDirectory()) {
        File nd = FileUtilities.createDirectory(Utilities.path(dest.getAbsolutePath(), f.getName()));
        processFiles(f, nd, "../"+relativeLocation+"/"+f.getName());
      } else if (f.getName().endsWith(".html")) {
        genRedirect(dest, f.getName(), relativeLocation);
      }
    }
  }

  private void genRedirect(File dest, String name, String relativeLocation) throws IOException {
    String redirect = "<!DOCTYPE HTML><html lang=\"en-US\"><head><meta charset=\"UTF-8\">\r\n"+
        "<meta http-equiv=\"refresh\" content=\"1; url={{url}}\">\r\n"+
        "<script type=\"text/javascript\">\r\n"+
        "  window.location.href = \"{{url}}\"\r\n"+
        "</script>\r\n"+
        "<title>Page Redirection</title></head><body>If you are not redirected automatically, follow this <a href='{{url}}'>link to Resource Index</a>.</body></html>\r\n";
    redirect = redirect.replace("{{url}}", relativeLocation+"/"+name);
    FileUtilities.stringToFile(redirect, Utilities.path(dest.getAbsolutePath(), name));
    total++;
  }
  
  
}
