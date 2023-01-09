package org.hl7.fhir.igtools.renderers;

import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.renderers.IMarkdownProcessor;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.r5.utils.TranslatingUtilities;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class BaseRenderer extends TranslatingUtilities implements IMarkdownProcessor {
  protected IWorkerContext context;
  protected String corePath;
  protected String prefix = ""; // path to relative root of IG, if not the same directory (currenly always is)
  protected IGKnowledgeProvider igp;
  protected List<SpecMapManager> specmaps;
  protected Set<String> allTargets;
  protected NpmPackage packge;
  protected MarkDownProcessor markdownEngine;
  protected RenderingContext gen;


  public BaseRenderer(IWorkerContext context, String corePath, IGKnowledgeProvider igp, List<SpecMapManager> specmaps, Set<String> allTargets, MarkDownProcessor markdownEngine, NpmPackage packge, RenderingContext gen) {
    super();
    this.context = context;
    this.corePath = corePath;
    this.igp = igp;
    this.specmaps = specmaps;
    this.markdownEngine = markdownEngine;
    this.packge = packge; 
    this.gen = gen;
    this.allTargets = allTargets;
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
	          if (p.getUserData("path") == null)
	            url = paths[0].toLowerCase();
	          else
	            url = p.getUserString("path");
	          if (paths.length > 1) {
	            url = url.replace(".html", "-definitions.html#"+parts[0]);
	          }
	        } else {
	          throw new Exception("Unresolved logical URL '"+linkText+"' in markdown");
	        }
	      }
	      text = left+"["+linkText+"]("+url+")"+right;
	    }
	    // 2. if prefix <> "", then check whether we need to insert the prefix
	    if (!Utilities.noString(prefix)) {
	      int i = text.length() - 3;
	      while (i > 0) {
	        if (text.substring(i, i+2).equals("](") && i+7 <= text.length()) {
	          // The following can go horribly wrong if i+7 > text.length(), thus the check on i+7 above and the Throwable catch around the whole method just in case. 
	          if (!text.substring(i, i+7).equals("](http:") && !text.substring(i, i+8).equals("](https:") && !text.substring(i, i+3).equals("](.")) { 
	            text = text.substring(0, i)+"]("+corePath+text.substring(i+2);
	          }
	        }
	        i--;
	      }
	    }
	    text = ProfileUtilities.processRelativeUrls(text, "", corePath, context.getResourceNames(), specmaps.get(0).listTargets(), allTargets, false);
	    // 3. markdown
	    String s = markdownEngine.process(text, location);
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

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

}
