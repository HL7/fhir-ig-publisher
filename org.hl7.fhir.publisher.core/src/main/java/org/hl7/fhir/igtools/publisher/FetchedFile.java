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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.validation.ValidationMessage;

public class FetchedFile {
  private static long timeZero = System.currentTimeMillis();
  private static String root;
  private static List<String> columns = new ArrayList<>();

  public class ProcessingReport {
    private String activity;
    private long start;
    private long finish;
  }
  
  public enum FetchedBundleType {
    NATIVE, SPREADSHEET
  }
  public final static int PROCESS_RESOURCE = 0;
  public final static int PROCESS_XSLT = 1;
  public final static int PROCESS_NONE = 2;
  
  private String path;
  private String relativePath;
  private String name;
  private String title;
  private byte[] xslt;
  
  private byte[] source;
  private long size;
  private long hash;
  @Getter @Setter private long calcHash;
  private long time;
  private String contentType;
  private List<FetchedFile> dependencies;
  private List<FetchedResource> resources = new ArrayList<FetchedResource>();
  private List<ValidationMessage> errors = new ArrayList<ValidationMessage>();
  private FetchedResource bundle;
  private FetchedBundleType bundleType;
  private Map<String, String> valuesetsToLoad = new HashMap<String, String>();
  private boolean folder;
  private List<String> files; // if it's a folder
  private int processMode;
  private Set<String> outputNames = new HashSet<String>();
  private String statedPath;  
  private List<String> additionalPaths;  
  private String logical;
  private List<ProcessingReport> processes = new ArrayList<>();
  private boolean loaded;
  private List<ValidationMessage> filteredMessages;
  private String loadPath;
  private Map<String, Boolean> translations = new HashMap<>();
  
  public FetchedFile(String statedPath) {
    super();
    this.statedPath = statedPath;
    this.loadPath = statedPath;
  }
  
  public FetchedFile(String statedPath, String loadPath) {
    super();
    this.statedPath = statedPath;
    this.loadPath = loadPath;
  }
  public String getPath() {
    return path;
  }
  public void setPath(String path) {
    this.path = path;
  }
  public String getRelativePath() {
    return relativePath;
  }
  public void setRelativePath(String relativePath) {
    this.relativePath = relativePath;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  public byte[] getXslt() {
    return xslt;
  }
  public void setXslt(byte[] xslt) {
    this.xslt = xslt;
  }

  public long getTime() {
    return time;
  }
  public void setTime(long time) {
    this.time = time;
  }
  public String getContentType() {
    return contentType;
  }
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }
 
  public List<FetchedFile> getDependencies() {
    return dependencies;
  }
  public void setDependencies(List<FetchedFile> dependencies) {
    this.dependencies = dependencies;
  }
  public long getHash() {
    return hash;
  }
  public void setHash(long hash) {
    this.hash = hash;
  }
  public byte[] getSource() {
    if (source == null)
      throw new Error("Source has been dropped");
    return source;
  }
  public void setSource(byte[] source) {
    this.source = source;
    this.size = source.length;
    this.hash = Arrays.hashCode(source);
  }
  

  public List<FetchedResource> getResources() {
    return resources;
  }
  public FetchedResource addResource(String nameForErrors) {
    FetchedResource r = new FetchedResource(nameForErrors);
    r.setTitle(getTitle());
    resources.add(r);
    return r;
  }
  public List<ValidationMessage> getErrors() {
    return errors;
  }
  public List<ValidationMessage> getFilteredMessages() {
    return filteredMessages;
  }
  public void setFilteredMessages(List<ValidationMessage> filteredMessages) {
    this.filteredMessages = filteredMessages;
  }
  public FetchedResource getBundle() {
    return bundle;
  }
  public void setBundle(FetchedResource bundle) {
    this.bundle = bundle;
  }
  public Map<String, String> getValuesetsToLoad() {
    return valuesetsToLoad;
  }
  public boolean isFolder() {
    return folder;
  }
  public void setFolder(boolean folder) {
    this.folder = folder;
  }
  public List<String> getFiles() {
    if (files == null)
      files = new ArrayList<String>();
    return files;
  }

  public Set<String> getOutputNames() {
    return outputNames;
  }
  public int getProcessMode() {
    return processMode;
  }
  public void setProcessMode(int processMode) {
    this.processMode = processMode;
  }
  public Boolean hasTitle() {
    return title != null;
  }
  public String getTitle() {
    return title == null ? name : title;
  }
  public void setTitle(String title) {
    this.title = title;
  }
  public boolean matches(FetchedFile other) {
    return this.path.equals(other.path);
  }
  public FetchedBundleType getBundleType() {
    return bundleType;
  }
  public void setBundleType(FetchedBundleType bundleType) {
    this.bundleType = bundleType;
  }
  @Override
  public String toString() {
    return "FetchedFile["+name+"]";
  }
  public String getStatedPath() {
    return statedPath;
  }
  public void trim() {
    source = null;
  }
  public String getLogical() {
    return logical;
  }
  public void setLogical(String logical) {
    this.logical = logical;
  }
  
  public void start(String activityName) {
    if (!columns.contains(activityName)) {
      columns.add(activityName);
    }
    ProcessingReport pr = new ProcessingReport();
    pr.activity = activityName;
    pr.start = System.currentTimeMillis();
    processes.add(pr);
  }
  
  public void finish(String activityName) {
    for (int i = processes.size() -1; i >= 0; i--) {
      ProcessingReport pr = processes.get(i);
      if (pr.activity.equals(activityName) && pr.finish == 0) {
        pr.finish = System.currentTimeMillis();
        return;
      }
    }
    throw new Error("No tracked activity for "+activityName);
  }
  
  public void processReport(FetchedFile f, JsonObject fj) {
    fj.add("name", root != null && statedPath.startsWith(root) ? statedPath.substring(root.length()) : statedPath);
    fj.add("size", size);
    for (ProcessingReport pr : processes) {
      long duration = (pr.finish - pr.start);
      if (duration > 0) {
        JsonObject jp = new JsonObject();
        fj.forceArray("processes").add(jp);
        jp.add("activity", pr.activity);
        jp.add("start", (pr.start - timeZero) / 1000);
        jp.add("length", duration);
      }
    }
    
  }
  public static String getRoot() {
    return root;
  }
  public static void setRoot(String root) {
    FetchedFile.root = root;
  }
  public static List<String> getColumns() {
    return columns;
  }
  
  public void appendReport(StringBuilder b) {
    b.append(statedPath.startsWith(root) ? statedPath.substring(root.length()) : statedPath);
    b.append("\t");
    b.append(""+size);
    for (String s : columns) {
      b.append("\t");
      ProcessingReport pr = null;
      for (ProcessingReport t : processes) {
         if (t.activity.equals(s)) {
           pr = t;
           break;
         }
      }
      if (pr == null) {
        b.append("-");
      } else {
        b.append(pr.finish - pr.start);
      }
    }
  }
  
  public long getSize() {
    return size;
  }
  public boolean isLoaded() {
    return loaded;
  }
  public void setLoaded(boolean loaded) {
    this.loaded = loaded;
  }
  public boolean hasAdditionalPaths() {
    return additionalPaths != null && !additionalPaths.isEmpty();
  }
  public List<String> getAdditionalPaths() {
    return additionalPaths;
  }
  public void addAdditionalPath(String additionalPath) {
    if (additionalPaths == null) {
      additionalPaths = new ArrayList<String>();
    }
    this.additionalPaths.add(additionalPath);
  }
  public String getLoadPath() {
    return loadPath;
  }
  public void setLoadPath(String loadPath) {
    this.loadPath = loadPath;
  }

  public void setTranslation(String l, boolean ok) {
    translations .put(l, ok);
    
  }

  public boolean getTranslated(String l) {
    if (translations.containsKey(l) && translations.get(l)) {
      return true;
    } else {
      return false;
    }
  }


  public void calculateHash() {
    if (calcHash == 0) {
      long result = hash;
      if (dependencies != null) {
        for (FetchedFile f : dependencies) {
          result = 37 * result + f.calculateHash(addToSet( null, this));
        }
      }
      calcHash = result;
    }
  }

  private long calculateHash(Set<FetchedFile> trail) {
    if (trail.contains(this)) {
      return 0;
    }
    if (calcHash == 0) {
      long result = hash;
      if (dependencies != null) {
        for (FetchedFile f : dependencies) {
          result = 37 * result + f.calculateHash(addToSet(trail, this));
        }
      }
      calcHash = result;
    }
    return calcHash;
  }

  private Set<FetchedFile> addToSet(Set<FetchedFile> trail, FetchedFile focus) {
    Set<FetchedFile> result = new HashSet<>();
    if (trail != null) {
      result.addAll(trail);
    }
    result.add(focus);
    return result;
  }

}
