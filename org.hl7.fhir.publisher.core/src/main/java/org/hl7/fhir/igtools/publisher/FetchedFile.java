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

import org.hl7.fhir.utilities.validation.ValidationMessage;

public class FetchedFile {
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
  private long hash;
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
  
  public FetchedFile(String statedPath) {
    super();
    this.statedPath = statedPath;
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
    this.hash =Arrays.hashCode(source);
  }
  

  public List<FetchedResource> getResources() {
    return resources;
  }
  public FetchedResource addResource() {
    FetchedResource r = new FetchedResource();
    r.setTitle(getTitle());
    resources.add(r);
    return r;
  }
  public List<ValidationMessage> getErrors() {
    return errors;
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
    for (FetchedResource r : resources) {
      r.trim();
    }
    
  }
  
}
