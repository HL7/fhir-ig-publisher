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


import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.conformance.ProfileUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.TranslatingUtilities;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.cache.NpmPackage;

public class BaseRenderer extends TranslatingUtilities {
  protected IWorkerContext context;
  protected String prefix;
  protected IGKnowledgeProvider igp;
  protected List<SpecMapManager> specmaps;
  protected NpmPackage packge;
  private MarkDownProcessor markdownEngine;
  protected RenderingContext gen;


  public BaseRenderer(IWorkerContext context, String prefix, IGKnowledgeProvider igp, List<SpecMapManager> specmaps, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super();
    this.context = context;
    this.prefix = prefix;
    this.igp = igp;
    this.specmaps = specmaps;
    this.markdownEngine = markdownEngine;
    this.packge = packge; 
    this.gen = gen;
  }

  @SuppressWarnings("rawtypes")
  public String processMarkdown(String location, PrimitiveType md) throws FHIRException {
    String text = gt(md);
    return processMarkdown(location, text);
  }
  
  public String processMarkdown(String location, String text) throws FHIRException {
	  try {
	    if (text == null) {
	      return "";
	    }
	    // 1. custom FHIR extensions
	    text = text.replace("||", "\r\n\r\n");
	    while (text.contains("[[[")) {
	      String left = text.substring(0, text.indexOf("[[["));
	      String linkText = text.substring(text.indexOf("[[[")+3, text.indexOf("]]]"));
	      String right = text.substring(text.indexOf("]]]")+3);
	      String url = getBySpecMap(linkText);
	      String[] parts = linkText.split("\\#");
	      
	      if (url == null && parts[0].contains("/StructureDefinition/")) {
	        StructureDefinition ed = context.fetchResource(StructureDefinition.class, parts[0]);
	        if (ed == null)
	          throw new Error("Unable to find extension "+parts[0]);
	        url = ed.getUserData("filename")+".html";
	      } 
	      if (Utilities.noString(url)) {
	        String[] paths = parts[0].split("\\.");
	        StructureDefinition p = new ProfileUtilities(context, null, null).getProfile(null, paths[0]);
	        if (p != null) {
	          String suffix = (paths.length > 1) ? "-definitions.html#"+parts[0] : ".html";
	          if (p.getUserData("filename") == null)
	            url = paths[0].toLowerCase()+suffix;
	          else
	            url = p.getUserData("filename")+suffix;
	        } else {
	          throw new Exception("Unresolved logical URL '"+linkText+"' in markdown");
	        }
	      }
	      text = left+"["+linkText+"]("+url+")"+right;
	    }
	    // 1. if prefix <> "", then check whether we need to insert the prefix
	    if (!Utilities.noString(prefix)) {
	      int i = text.length() - 3;
	      while (i > 0) {
	        if (text.substring(i, i+2).equals("](") && i+7 <= text.length()) {
	          // The following can go horribly wrong if i+7 > text.length(), thus the check on i+7 above and the Throwable catch around the whole method just in case. 
	          if (!text.substring(i, i+7).equals("](http:") && !text.substring(i, i+8).equals("](https:") && !text.substring(i, i+3).equals("](.")) { 
	            text = text.substring(0, i)+"]("+prefix+text.substring(i+2);
	          }
	        }
	        i--;
	      }
	    }
	    // 3. markdown
	    String s = markdownEngine.process(checkEscape(text), location);
	    return s;
	  } catch (Throwable e) {
		  throw new FHIRException("Error processing string: " + text, e);
	  }

  }

  private String getBySpecMap(String linkText) throws Exception {
    for (SpecMapManager map : specmaps) {
      String url = map.getPage(linkText);
      if (url != null)
        return Utilities.pathURL(map.getBase(), url);
    }      
    return null;
  }

  private String checkEscape(String text) {
    if (text.startsWith("```"))
      return text.substring(3);
    else
      return Utilities.escapeXml(text);
  }

  protected String canonicalise(String uri) {
    if (uri == null) {
      return null;
    }
    if (!uri.startsWith("http:") && !uri.startsWith("https:"))
      return igp.getCanonical()+"/"+uri;
    else
      return uri;
  }

  protected String renderCommitteeLink(CanonicalResource cr) {
    String code = ToolingExtensions.readStringExtension(cr, ToolingExtensions.EXT_WORKGROUP);
    CodeSystem cs = context.fetchCodeSystem("http://terminology.hl7.org/CodeSystem/hl7-work-group");
    if (cs == null || !cs.hasUserData("path"))
      return code;
    else {
      ConceptDefinitionComponent cd = CodeSystemUtilities.findCode(cs.getConcept(), code);
      if (cd == null) {
        return code;        
      } else {
        return "<a href=\""+cs.getUserString("path")+"#"+cs.getId()+"-"+cd.getCode()+"\">"+cd.getDisplay()+"</a>";
      }
    }
  }

}
