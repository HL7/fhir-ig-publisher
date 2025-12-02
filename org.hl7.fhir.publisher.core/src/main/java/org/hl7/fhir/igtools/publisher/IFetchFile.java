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


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.DataType;

public interface IFetchFile {
  
  public enum FetchState { NOT_FOUND, DIR, FILE }
  FetchState check(String path);
  String pathForFile(String path) throws IOException;
  
  FetchedFile fetch(String path) throws Exception;
  FetchedFile fetchFlexible(String path) throws Exception;
  boolean canFetchFlexible(String path) throws Exception;
  FetchedFile fetch(DataType source, FetchedFile base) throws Exception;
  FetchedFile fetchResourceFile(String name) throws Exception; 
  void setPkp(IGKnowledgeProvider pkp);
  List<FetchedFile> scan(String sourceDir, IWorkerContext context, boolean autoScan, List<String> exemptions) throws IOException, FHIRException;

  public ILoggingService getLogger();
  public void setLogger(ILoggingService log);
  void setResourceDirs(List<String> theResourceDirs);
  InputStream openAsStream(String filename) throws FileNotFoundException;
  String openAsString(String path) throws FileNotFoundException, IOException;
  void setRootDir(String rootDir);
  void scanFolders(String dir, List<String> extraDirs);
}
