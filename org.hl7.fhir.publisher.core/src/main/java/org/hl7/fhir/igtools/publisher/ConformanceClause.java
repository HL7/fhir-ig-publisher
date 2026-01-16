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
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.HTMLInspector.LoadedFile;
import org.hl7.fhir.r5.model.ActorDefinition;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Requirements;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

class ConformanceClause {
    private ConformanceStatementHandler handler;
    private LoadedFile source;
    private String id;
    private String summary;
    private List<String> expectations;
    private boolean conditional = false;
    private XhtmlNode heading;
    private XhtmlNode node;
    private XhtmlNode origNode;
    boolean div;
    private List<ActorDefinition> actors = new ArrayList<ActorDefinition>();
    private List<Coding> categories = new ArrayList<Coding>();

    public ConformanceClause(ConformanceStatementHandler csHandler, XhtmlNode heading, XhtmlNode node, LoadedFile source, String metadata, String lang) {
      this(csHandler, heading, node, source, metadata, lang, null);
    }
    
    public ConformanceClause(ConformanceStatementHandler csHandler, XhtmlNode heading, XhtmlNode node, LoadedFile source, String metadata, String lang,String summary) {
      this.handler = csHandler;
      this.heading = heading;
      this.node = node;
      this.origNode = new XhtmlNode(NodeType.Element, "p");
      origNode.addChild(node.copy());
      String[] pathParts = source.path.split("\\/");
      fixRefs(origNode, pathParts[pathParts.length-1]);
      this.source = source;
      this.summary = summary;
      if (summary!=null)
        div = true;
      String text = node.toLiteralText();
      this.expectations = new ArrayList<>();
      handler.extractExpectations(this.expectations, text);
      if (this.expectations.isEmpty())
        throw new FHIRException("No conformance term found in the text: " + text);
      
      for (String expectation: expectations) {
        handler.expectationFound(expectation);
      }
      
      if (metadata == null) {
        id = handler.nextStatementId(lang);
        return;
      }
      
      String actorString = "";
      String categoryString = "";
      
      if (!metadata.contains("^")) {
        id = metadata.trim();
      } else {
        id = StringUtils.substringBefore(metadata, "^").trim();
        String remainder = StringUtils.substringAfter(metadata, "^").trim();
        if (!remainder.contains("^")) {
          actorString = remainder.trim();
        } else {
          actorString = StringUtils.substringBefore(remainder, "^").trim();
          categoryString = StringUtils.substringAfter(remainder, "^").trim();
        }
      }
      
      if (id.endsWith("?")) {
        conditional = true;
        id = StringUtils.substringBefore(id,  "?");
      }
      
      if (!actorString.equals("")) {
        String[] actorList = actorString.split(",");
        for (String actorId: actorList) {
          ActorDefinition anActor = handler.findActor(actorId.trim());
          if (anActor==null)
            throw new FHIRException("Unable to find actor with id " + actorId + " - " + metadata);
          else if (actors.contains(anActor))
            throw new FHIRException("Conformance statement has two references to the same actor id " + actorId + " - " + metadata);
          else {
            actors.add(anActor);
            handler.actorUsed(anActor);
          }
        }
      }

      if (!categoryString.equals("")) {
        String[] categoryList = categoryString.split(",");
        for (String code: categoryList) {
          Coding category = handler.findCategory(code.trim());
          if (category==null)
            throw new FHIRException("Unable to find matching code in category value set for " + code + " - " + metadata);
          else if (categories.contains(category))
            throw new FHIRException("Conformance statement has two references to the same category " + code + " - " + metadata);
          else {
            categories.add(category);
            handler.categoryUsed(category);
          }
        }
      }
    }
    
    private void fixRefs(XhtmlNode node, String fileName) {
      if (node.getNodeType().equals(NodeType.Element) && node.getName().equals("a") && node.hasAttribute("href")) {
        if (node.getAttribute("href").startsWith("#")) {
          node.setAttribute("href", fileName + node.getAttribute("href"));
        }
      } else if (node.hasChildren()) {
        for (XhtmlNode child: node.getChildNodes()) {
          fixRefs(child, fileName);
        }
      }
    }

    public LoadedFile getSource() {
      return source;
    }
    
    public String getId() {
      return id;
    }
    
    public boolean hasSummary() {
      return StringUtils.isNotEmpty(summary);
    }
    
    public String getSummary() {
      return summary;
    }
    
    public List<String> getExpectations() {
      return expectations;
    }

    public boolean isConditional() {
      return conditional;
    }

    public XhtmlNode getNode() {
      return node;
    }
    
    public XhtmlNode getOrigNode() {
      return origNode;
    }
    
    public boolean isDiv() {
      return div;
    }
    
    public boolean hasActors() {
      return !actors.isEmpty();
    }
    
    public List<ActorDefinition> getActors() {
      return actors;
    }

    public boolean hasCategories() {
      return !categories.isEmpty();
    }
    
    public List<Coding> getCategories() {
      return categories;
    }    
}