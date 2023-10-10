package org.hl7.fhir.igtools.publisher;

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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.SimpleFetcher.FetchedFileSorter;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService;
import org.hl7.fhir.r5.context.IWorkerContext.ILoggingService.LogCategory;
import org.hl7.fhir.r5.elementmodel.FmlParser;
import org.hl7.fhir.r5.elementmodel.ValidatedFragment;
import org.hl7.fhir.r5.formats.FormatUtilities;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.UriType;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;

public class SimpleFetcher implements IFetchFile {

  public class FetchedFileSorter implements Comparator<FetchedFile> {

    @Override
    public int compare(FetchedFile o1, FetchedFile o2) {
      return o1.getPath().compareTo(o2.getPath());
    }

  }

  private static final String[] EXTENSIONS = new String[] {".xml", ".json", ".map", ".fml", ".phinvads"};
  private IGKnowledgeProvider pkp;
  private List<String> resourceDirs;
  private ILoggingService log;
  private String rootDir;
  private FmlParser fp;
  private boolean debug = false;
  private boolean report = true;

  
  public SimpleFetcher(ILoggingService log) {
    this.log = log;
  }

  @Override
  public void setResourceDirs(List<String> resourceDirs) {
    this.resourceDirs = resourceDirs;
  }
  
  @Override
  public void setPkp(IGKnowledgeProvider pkp) {
    this.pkp = pkp;
  }

  public String getRootDir() {
    return rootDir;
  }

  @Override
  public void setRootDir(String rootDir) {
    this.rootDir = rootDir;
    FetchedFile.setRoot(rootDir);
  }

  @Override
  public FetchedFile fetch(String path) throws Exception {
    File f = new File(path);
    if (!f.exists())
      throw new Exception("Unable to find file "+path);
    FetchedFile ff = new FetchedFile(path);
    ff.setPath(f.getCanonicalPath());
    ff.setName(f.isDirectory() ? path : fileTitle(path));
    ff.setTime(f.lastModified());
    if (f.isDirectory()) {
      ff.setContentType("application/directory");
      ff.setFolder(true);   
      for (File fl : f.listFiles()) {
        if (!isIgnoredFile(fl.getName())) {
          ff.getFiles().add(fl.getCanonicalPath());
        }
      }
    } else if (!isIgnoredFile(f.getName())) {
      ff.setFolder(false);   
      if (path.endsWith("json")) {
        ff.setContentType("application/fhir+json");
      } else if (path.endsWith("xml")) {
        ff.setContentType("application/fhir+xml");
      }
      InputStream ss = new FileInputStream(f);
      byte[] b = new byte[ss.available()];
      ss.read(b, 0, ss.available());
      ff.setSource(b);
      ss.close();
    }
    return ff;
  }

  private boolean isIgnoredFile(String name) {
    return name.startsWith(".");
  }

  @Override
  public FetchedFile fetchFlexible(String name) throws Exception {
    File f = null;
    String path = null;
    for (String dir : resourceDirs) {
      path = Utilities.path(dir, name);
      f = new File(path+".xml");
      if (f.exists()) {
        break;
      } else {
        f = new File(path+".json");
        if (f.exists()) {
          break;
        }
      }
    }
    if (f==null) {
      throw new Exception("Unable to find file "+path+".xml or "+path+".json");
    }
    FetchedFile ff = new FetchedFile(new File(rootDir).toURI().relativize(new File(path).toURI()).getPath());
    ff.setPath(f.getCanonicalPath());
    ff.setName(fileTitle(path));
    ff.setTime(f.lastModified());
    if (f.getName().endsWith("json")) {
      ff.setContentType("application/fhir+json");
    } else if (f.getName().endsWith("xml")) {
      ff.setContentType("application/fhir+xml");
    }
    InputStream ss = new FileInputStream(f);
    byte[] b = new byte[ss.available()];
    ss.read(b, 0, ss.available());
    ff.setSource(b);
    ss.close();
    return ff;
  }

  @Override
  public FetchedFile fetchResourceFile(String name) throws Exception {
    for (String dir: resourceDirs) {
      try {
        return fetch(Utilities.path(dir, name));
      } catch (Exception e) {
        // If we didn't find it, keep trying
      }
    }
    throw new Exception("Unable to find resource file "+name);
  }
  
  static public String fileTitle(String path) {
    if (path.contains(".")) {
      String ext = path.substring(path.lastIndexOf(".")+1);
      if (Utilities.isInteger(ext)) {
        return path;
      } else {
        return path.substring(0, path.lastIndexOf("."));
      }
    } else
      return path;
  }

  @Override
  public boolean canFetchFlexible(String name) throws Exception {
    for (String dir : resourceDirs) {
      if (new File(dir + File.separator + name + ".xml").exists()) {
        return true;
      } else if(new File(dir + File.separator + name + ".json").exists()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public FetchedFile fetch(DataType source, FetchedFile src) throws Exception {
    if (source instanceof Reference || source instanceof CanonicalType) {
      String s = source instanceof CanonicalType ? source.primitiveValue() : ((Reference)source).getReference();
      if (!s.contains("/"))
        throw new Exception("Bad Source Reference '"+s+"' - should have the format [Type]/[id]");
      String type = s.substring(0,  s.indexOf("/"));
      String id = s.substring(s.indexOf("/")+1); 
      
      if (!pkp.getContext().hasResource(StructureDefinition.class, "http://hl7.org/fhir/StructureDefinition/"+type) &&
          // first special case: Conformance/Capability
          (!(pkp.getContext().hasResource(StructureDefinition.class , "http://hl7.org/fhir/StructureDefinition/Conformance") && 
              type.equals("CapabilityStatement"))) 
          )
        throw new Exception("Bad Resource Identity - should have the format [Type]/[id] where Type is a valid resource type: " + s);
      if (!id.matches(FormatUtilities.ID_REGEX))
        throw new Exception("Bad Source Reference '"+s+"' - should have the format [Type]/[id] where id is a valid FHIR id type");
      String fn = pkp.getSourceFor(type+"/"+id);
      List<String> dirs = new ArrayList<>();
      dirs.add(Utilities.getDirectoryForFile(src.getPath()));
      dirs.addAll(resourceDirs);
      
      if (Utilities.noString(fn)) {
        // no source in the json file.
        fn = findFileInSet(dirs, 
              type.toLowerCase()+"-"+id,
              id+"."+type.toLowerCase(), // Added to support Forge's file naming convention
              type.toLowerCase()+"/"+id,
              id,
              type+"-"+id,
              id+"."+type,
              type+"/"+id);
        if (fn == null && "Binary".equals(type)) {
          fn = findAnyFile(dirs, 
              type.toLowerCase()+"-"+id,
              id+"."+type.toLowerCase(), // Added to support Forge's file naming convention
              type.toLowerCase()+"/"+id,
              id,
              type+"-"+id,
              id+"."+type,
              type+"/"+id);
        }
        if (fn == null)
          throw new Exception("Unable to find the source file for "+type+"/"+id+": not specified, so tried "+type+"-"+id+".xml, "+id+"."+type+".xml, "+type+"-"+id+".json, "+type+"/"+id+".xml, "+type+"/"+id+".json, "+id+".xml, and "+id+".json (and lowercase resource name variants) in dirs "+dirs.toString());
      } else {
        fn = findFile(dirs, fn);
        if (fn == null || !exists(fn))
          throw new Exception("Unable to find the source file for "+type+"/"+id+" at "+fn);
      }
      return fetch(fn); 
    } else if (source instanceof UriType) {
      UriType s = (UriType) source;
      String fn = Utilities.path(Utilities.getDirectoryForFile(src.getPath()), s.getValueAsString());
      return fetch(fn); 
    } else {
      throw new Exception("Unknown source reference type for implementation guide");
    }
  }
  
  String findAnyFile(List<String> dirs, String... names) throws IOException {
    for (String f : names) {
      String fn = findFileAnyExt(dirs, f);
      if (fn != null) {
        return fn;
      }
    }
    return null;
  }

  String findFileAnyExt(List<String> dirs, String name) throws IOException {
    for (String dir : dirs) {
      for (String fn : new File(dir).list()) {
        if (fn.startsWith(name+".")) {
          return Utilities.path(dir, fn);
        }
      }
    }
    return null;
  }

  String findFileInSet(List<String> dirs, String... names) throws IOException {
    for (String f : names) {
      String fn = findFileMultiExt(dirs, f);
      if (fn != null) {
        return fn;
      }
    }
    return null;
  }

  String findFileMultiExt(List<String> dirs, String name) throws IOException {
    for (String ex : EXTENSIONS) {
      String fn = findFile(dirs, name+ex);
      if (fn != null ) {
        return fn;
      }
    }
    return null;
  }

  String findFile(List<String> dirs, String name) throws IOException {
    for (String dir : dirs) {
      String fn = Utilities.path(dir, name);
      if (new File(fn).exists())
        return fn;
    }
    return null;
  }

  private boolean exists(String fn) {
    return new File(fn).exists();
  }
  
  @Override
  public List<FetchedFile> scan(String sourceDir, IWorkerContext context, boolean autoPath) throws IOException, FHIRException {
    List<String> sources = new ArrayList<String>();
    if (sourceDir != null)
      sources.add(sourceDir);
    if (autoPath)
      sources.addAll(resourceDirs);
    if (sources.isEmpty())
      throw new FHIRException("No Source directories to scan found"); // though it's not possible to get to this point...

    List<FetchedFile> res = new ArrayList<>();
    for (String s : Utilities.sorted(sources)) {
      int count = 0;
      File file = new File(s);
      if (file.exists()) {
        for (File f : file.listFiles()) {
          if (!f.isDirectory()) {
//            System.out.println("scanning: "+f.getAbsolutePath());
            String fn = f.getCanonicalPath();
            String ext = Utilities.getFileExtension(fn);
            if (!Utilities.existsInList(ext, "md", "txt") && !fn.endsWith(".gitignore") && !fn.contains("-spreadsheet") && !isIgnoredFile(f.getName())) {
              boolean ok = false;
              if (!Utilities.existsInList(ext, fixedFileTypes()))
                try {
                  org.hl7.fhir.r5.elementmodel.Element e = new org.hl7.fhir.r5.elementmodel.XmlParser(context).parseSingle(new FileInputStream(f), null);
                  addFile(res, f, e, "application/fhir+xml");
                  count++;
                  ok = true;
                } catch (Exception e) {
                  if (!f.getName().startsWith("Binary-") && !f.getName().startsWith("binary-") ) { // we don't notify here because Binary is special. 
                    if (report) {
                      log.logMessage("Error loading "+f+" as XML: "+e.getMessage());
                      if (debug) {
                        e.printStackTrace();
                      }
                    }
                  }
                }
              if (!ok && !Utilities.existsInList(ext, "xml", "ttl", "html", "txt", "fml")) {
                try {
                  List<ValidatedFragment> el = new org.hl7.fhir.r5.elementmodel.JsonParser(context).parse(new FileInputStream(fn));
                  if (el.size() == 1) {
                    addFile(res, f, el.get(0).getElement(), "application/fhir+json");
                    count++;
                    ok = true;
                  }
                } catch (Exception e) {
                  if (!f.getName().startsWith("Binary-")) { // we don't notify here because Binary is special. 
                    if (report) {
                      log.logMessage("Error loading "+f+" as JSON: "+e.getMessage());
                      if (debug) {
                        e.printStackTrace();
                      }
                    }
                  }
                }
              }
              if (!ok && !Utilities.existsInList(ext, "json", "xml", "html", "txt", "fml")) {
                try {
                  org.hl7.fhir.r5.elementmodel.Element e = new org.hl7.fhir.r5.elementmodel.TurtleParser(context).parseSingle(new FileInputStream(fn), null);
                  addFile(res, f, e, "application/fhir+turtle");
                  count++;
                  ok = true;
                } catch (Exception e) {
                  if (!f.getName().startsWith("Binary-")) { // we don't notify here because Binary is special. 
                    if (report) {
                      log.logMessage("Error loading "+f+" as Turtle: "+e.getMessage());
                      if (debug) {
                        e.printStackTrace();
                      }
                    }

                  }
                }
              }              
              if (!ok && !Utilities.existsInList(ext, "json", "xml", "html", "txt")) {
                try {
                  if (fp==null) {
                    fp = new FmlParser(context);
                  }
                  org.hl7.fhir.r5.elementmodel.Element e  = fp.parse(new FileInputStream(f)).get(0).getElement();
                  addFile(res, f, e, "fml");
                  count++;
                  ok = true;
                } catch (Exception e) {
                  if (!f.getName().startsWith("Binary-")) { // we don't notify here because Binary is special. 
                    if (report) {
                      log.logMessage("Error loading "+f+" as FML: "+e.getMessage());
                      if (debug) {
                        e.printStackTrace();
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      log.logDebugMessage(LogCategory.PROGRESS, "Loaded "+Integer.toString(count)+" files from "+s);
    }
    Collections.sort(res, new FetchedFileSorter());
    return res;
  }

  private List<String> fixedFileTypes() {
    return Utilities.strings(
        // known file types we have parsers for
        "json", "ttl", "html", "txt", "fml", 
        
        // known files types to not even try parsing
        "jpg", "png", "gif", "mp3", "mp4", "pfd", "doc", "docx", "ppt", "pptx", "svg");
  }

  private void addFile(List<FetchedFile> res, File f, org.hl7.fhir.r5.elementmodel.Element e, String cnt) throws IOException {
    if (!e.fhirType().equals("ImplementationGuide"))
      addFile(res, f, cnt);
  }
  
  private void addFile(List<FetchedFile> res, File f, String cnt) throws IOException {
    FetchedFile ff = new FetchedFile(new File(rootDir).toURI().relativize(f.toURI()).getPath());
    ff.setPath(f.getCanonicalPath());
    ff.setName(fileTitle(f.getCanonicalPath()));
    ff.setTime(f.lastModified());
    ff.setFolder(false);   
    ff.setContentType(cnt);
    InputStream ss = new FileInputStream(f);
    byte[] b = new byte[ss.available()];
    ss.read(b, 0, ss.available());
    ff.setSource(b);
    ss.close();
    res.add(ff);    
  }

  public ILoggingService getLogger() {
    return log;
  }

  public void setLogger(ILoggingService log) {
    this.log = log;
  }

  @Override
  public FetchState check(String path) {
    File f = new File(path);
    if (!f.exists())
      return FetchState.NOT_FOUND;
    else if (f.isDirectory())
      return FetchState.DIR;
    else
      return FetchState.FILE;
  }

  @Override
  public String pathForFile(String path) {
    return Utilities.getDirectoryForFile(path);
  }

  @Override
  public InputStream openAsStream(String filename) throws FileNotFoundException {
    return new FileInputStream(filename);
  }

  @Override
  public String openAsString(String filename) throws IOException {
    return TextFile.fileToString(filename);
  }

  @Override
  public void scanFolders(String dir, List<String> dirs) {
    scanFolders(new File(dir), dirs);
  }
  
  public void scanFolders(File dir, List<String> dirs) {
    dirs.add(dir.getAbsolutePath());
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        scanFolders(f, dirs);
      }
    }    
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public boolean isReport() {
    return report;
  }

  public void setReport(boolean report) {
    this.report = report;
  }

   
}
