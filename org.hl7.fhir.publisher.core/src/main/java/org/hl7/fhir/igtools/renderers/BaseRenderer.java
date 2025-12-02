package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.igtools.publisher.IGKnowledgeProvider;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.PrimitiveType;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.renderers.IMarkdownProcessor;
import org.hl7.fhir.r5.renderers.RendererFactory;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.Resolver.ResourceWithReference;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.StringPair;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class BaseRenderer implements IMarkdownProcessor {
  protected IWorkerContext context;
  protected String corePath;
  protected String prefix = ""; // path to relative root of IG, if not the same directory (currently always is)
  protected IGKnowledgeProvider igp;
  protected List<SpecMapManager> specmaps;
  protected Set<String> allTargets;
  protected NpmPackage packge;
  protected MarkDownProcessor markdownEngine;
  protected RenderingContext gen;
  protected List<FetchedFile> fileList;


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

  public List<FetchedFile> getFileList() {
    return fileList;
  }

  public void setFileList(List<FetchedFile> fileList) {
    this.fileList = fileList;
  }

  @SuppressWarnings("rawtypes")
  public String processMarkdown(String location, PrimitiveType md) throws FHIRException {
    String text = gen.getTranslated(md);
    return processMarkdown(location, text);
  }
  
  public String preProcessMarkdown(String location, String text) throws Exception {
    if (text == null) {
      return "";
    }
    // 1. custom FHIR extensions
    text = text.replace("||", "\r\n\r\n");
    while (text.contains("[[[")) {
      String left = text.substring(0, text.indexOf("[[["));
      String linkText = text.substring(text.indexOf("[[[")+3, text.indexOf("]]]")).trim();
      String right = text.substring(text.indexOf("]]]")+3);
      String url = null;

      String display = linkText;
      StringPair pp = gen.getNamedLinks().get(linkText);
      if (pp != null) {
        url = pp.getName();
        display = pp.getValue();
      }
      if (url == null) {
        pp = getBySpecMap(linkText);
        if (pp != null) {
          url = pp.getName();
          if (pp.getValue() != null) {
            display = pp.getValue();
          }
        }
      }
      String[] parts = linkText.split("\\#");
      

      if (url == null) {
        Resource r = context.fetchResource(Resource.class, parts[0]);
        if (r == null && Utilities.isAbsoluteUrl(parts[0])) {
          ResourceWithReference rr = gen.getResolver().resolve(gen, parts[0], null);
          if (rr != null) {
            if (rr.getResource() != null) {
              display = RendererFactory.factory(rr.getResource(), gen).buildSummary(rr.getResource());
            }
            url = rr.getWebPath();
          }
        }
        if (r == null && Utilities.isAbsoluteUrl(parts[0])) {
          r = gen.findLinkableResource(Resource.class, parts[0]);
        }
        if (r == null) {
          // well, that failed; we'll try to interpret that as a link to a path in FHIR
          String rt = parts[0].contains(".") ? parts[0].substring(0, parts[0].indexOf(".")) : parts[0];
          StructureDefinition sd = context.fetchTypeDefinition(rt);
          if (sd != null && sd.getSnapshot().getElementByPath(parts[0]) != null) {
            r = sd;
          }
        } 
        if (r == null) {
          url = null;
        } else {
          if (r.hasWebPath()) {
            url = r.getWebPath();
          } else {
            url = r.getUserData(UserDataNames.render_filename)+".html";
          }
          if (r instanceof CanonicalResource ) {
            display = ((CanonicalResource) r).present();
          }
        }
      } 
      if (Utilities.noString(url)) {
        String[] paths = parts[0].split("\\.");
        StructureDefinition p = new ProfileUtilities(context, null, null).getProfile(null, paths[0]);
        if (p != null) {
          if (p.getWebPath() == null)
            url = paths[0].toLowerCase();
          else
            url = p.getWebPath();
          if (paths.length > 1) {
            url = url.replace(".html", "-definitions.html#"+parts[0]);
          }
          text = left+"["+display+"]("+url+")"+right;
        } else {
          text = left+"`"+display+"`"+right;
        }
      } else {
        text = left+"["+display+"]("+url+")"+right;
      }
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
    return text;
  }
  public String processMarkdown(String location, String text) throws FHIRException {
    if (text == null) {
      return "";
    }
	  try {
	    text = preProcessMarkdown(location, text);
	    String s = markdownEngine.process(text, location);
	    return s;
	  } catch (Throwable e) {
		  throw new FHIRException("Error processing string: " + text, e);
	  }
  }

  private StringPair getBySpecMap(String linkText) throws Exception {
    for (SpecMapManager map : specmaps) {
      String url = map.getPage(linkText);
      if (url != null)
        return new StringPair(Utilities.pathURL(map.getBase(), url), null);
    }      
    CanonicalResource cr = (CanonicalResource) context.fetchResource(Resource.class, linkText);
    if (cr != null && cr.hasWebPath()) {
      return new StringPair(cr.getWebPath(), cr.present());
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
    String code = ExtensionUtilities.readStringExtension(cr, ExtensionDefinitions.EXT_WORKGROUP);
    CodeSystem cs = context.fetchCodeSystem("http://terminology.hl7.org/CodeSystem/hl7-work-group");
    if (cs == null || !cs.hasWebPath())
      return code;
    else {
      ConceptDefinitionComponent cd = CodeSystemUtilities.findCode(cs.getConcept(), code);
      if (cd == null) {
        return code;        
      } else {
        return "<a href=\""+cs.getWebPath()+"#"+cs.getId()+"-"+cd.getCode()+"\">"+cd.getDisplay()+"</a>";
      }
    }
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }


  protected String getProvenanceReferences(Resource src) throws IOException {
    XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
    div.para(gen.formatPhrase(RenderingContext.PROV_REFERENCE, src.fhirType()));
    XhtmlNode ul = div.ul();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.isExample()) {
          if ("Provenance".equals(r.fhirType())) {
            for (Element e : r.getElement().getChildrenByName("target")) {
              String ref = e.getNamedChildValue("reference");
              if (("CodeSystem/" + src.getId()).equals(ref)) {
                ul.li().ah(r.getPath()).tx("Provenance " + (r.getTitle() == null ? r.getId() : r.getTitle()));
              }
            }
          }
        }
      }
    }
    String pr = ul.getChildNodes().isEmpty() ? "" : new XhtmlComposer(true, false).compose(div.getChildNodes());
    return pr;
  }
}
