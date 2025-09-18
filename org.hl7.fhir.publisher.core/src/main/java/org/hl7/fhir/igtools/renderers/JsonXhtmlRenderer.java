package org.hl7.fhir.igtools.renderers;

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


import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r5.formats.JsonCreator;
import org.hl7.fhir.utilities.Utilities;

public class JsonXhtmlRenderer implements JsonCreator {

  StringBuilder b;
  
  private String indent = "  ";
  
  private class LevelInfo {
    private boolean started;
    private boolean inElide;
    private boolean isArr;
    public LevelInfo(boolean isArr) {
      super();
      this.inElide = false;
      this.started = false;
      this.isArr = isArr;
    }
  }
  private List<LevelInfo> levels = new ArrayList<LevelInfo>();
  private String href;
  private List<String> comments = new ArrayList<>();
  
  @Override
  public void comment(String contents) {
    comments.add(contents);
  }

  private boolean prism;

  public boolean isPrism() {
    return prism;
  }

  public void setPrism(boolean prism) {
    this.prism = prism;
  }

  @Override
  public void beginObject() throws IOException {
    checkInArray();
    if (b == null) {
      b = new StringBuilder();
      if (prism) {
        b.append("<pre class=\"json\" data-fhir=\"generated\" style=\"white-space: pre; text-wrap: nowrap; width: auto;\"><code class=\"language-json\" style=\"white-space: pre; text-wrap: nowrap;\">");
      } else {
        b.append("<pre class=\"json\" data-fhir=\"generated\" style=\"white-space: pre; text-wrap: nowrap; width: auto;\"><code style=\"white-space: pre; text-wrap: nowrap;\">");
      }
    }
    commitComments();
    levels.add(0, new LevelInfo(false));
    b.append("{\r\n");
    for (int i = 0; i < levels.size(); i++) 
      b.append(indent);
  }

  

  private void commitComments() {
    for (String s : comments) {
      b.append("<span style=\"font-style: italic; color: grey\">// ");
      b.append(Utilities.escapeXml(s));
      b.append("</span>\r\n");
      for (int i = 0; i < levels.size(); i++) 
        b.append(indent);      
    }
    comments.clear();
  }

  @Override
  public void endObject() throws IOException {
    levels.remove(0);
    b.append("\r\n");
    for (int i = 0; i < levels.size(); i++) 
      b.append(indent);
    b.append("}");
  }

  @Override
  public void name(String name) throws IOException {
    if (levels.get(0).isArr)
      throw new IOException("Error producing JSON: attempt to use name in an array");
    
    if (levels.get(0).started) {
      if (!levels.get(0).inElide)
        b.append(",");
      b.append("\r\n");
      for (int i = 0; i < levels.size(); i++) 
        b.append(indent);
    } else
      levels.get(0).started = true;
    levels.get(0).inElide = false;
    b.append('"');
    if (href != null)
      b.append("<a href=\""+href+"\">");
    b.append(Utilities.escapeXml(name));
    if (href != null)
      b.append("</a>");
    b.append('"');
    b.append(" : ");
    href = null;
  }


  @Override
  public void nullValue() throws IOException {
    checkInArray();
    b.append("null");
  }

  private void checkInArray() {
    if (levels.size() > 0 && levels.get(0).isArr) {
      if (levels.get(0).started) {
        if (!levels.get(0).inElide)
          b.append(",");
        b.append("\r\n");
        for (int i = 0; i < levels.size(); i++) 
          b.append(indent);
      } else
        levels.get(0).started = true;
      levels.get(0).inElide = false;
    }
  }

  @Override
  public void value(String value) throws IOException {
    checkInArray();
    b.append('"');
    b.append(Utilities.escapeXml(Utilities.escapeJson(value)));
    b.append('"');
  }

  @Override
  public void value(Boolean value) throws IOException {
    checkInArray();
    b.append(value ? "true" : "false");
  }

  @Override
  public void value(BigDecimal value) throws IOException {
    checkInArray();
    b.append(value.toString());
  }

  @Override
  public void valueNum(String value) throws IOException {
    checkInArray();
    b.append(value);
  }

  @Override
  public void value(Integer value) throws IOException {
    checkInArray();
    b.append(value.toString());
  }

  @Override
  public void beginArray() throws IOException {
    checkInArray();
    levels.add(0, new LevelInfo(true));
    b.append("[\r\n");
    for (int i = 0; i < levels.size(); i++) 
      b.append(indent);

  }

  @Override
  public void endArray() throws IOException {
    levels.remove(0);
    b.append("\r\n");
    for (int i = 0; i < levels.size(); i++) 
      b.append(indent);
    b.append("]");
  }

  @Override
  public void finish() throws IOException {
    b.append("</code></pre>\r\n");
  }

  @Override
  public String toString() {
    return b.toString();
  }

  @Override
  public void link(String href) {
    this.href = href;
  }

  @Override
  public void anchor(String name) {
    b.append("<a name=\""+name+"\"></a>");
  }

  @Override
  public void externalLink(String ref) {
    b.append("<a href=\""+Utilities.escapeXml(ref)+"\" style=\"color: Maroon\">&#x1F517;</a> ");
  }

  @Override
  public boolean canElide() { return true; }

  @Override
  public void elide() {
    if (levels.get(0).started) {
      if (!levels.get(0).inElide)
        b.append(",");
      if (!b.toString().endsWith("\r\n") && !b.toString().endsWith(" "))
        b.append("\r\n");
    } else {
      levels.get(0).started = true;
      if (!b.toString().endsWith("\r\n") && !b.toString().endsWith(" "))
        b.append("\r\n");
    }
    levels.get(0).inElide = true;
    if (!b.toString().endsWith(" ")) {
      for (int i = 0; i < levels.size(); i++)
        b.append(indent);
    }
    b.append("...");
  }
  
  @Override
  public boolean isCanonical() {
    return false;
  }

}
