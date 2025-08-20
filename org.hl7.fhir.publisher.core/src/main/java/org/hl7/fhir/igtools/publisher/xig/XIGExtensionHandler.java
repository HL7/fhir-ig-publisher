package org.hl7.fhir.igtools.publisher.xig;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.terminologies.JurisdictionUtilities;
import org.hl7.fhir.r5.utils.ResourceSorters.CanonicalResourceSortByUrl;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;

public class XIGExtensionHandler {

  private Map<String, StructureDefinition> extensions = new HashMap<>();
  private Map<String, Set<String>> extensionUsage = new HashMap<>();
  
  public void seeExtension(StructureDefinition sd) {
//    System.out.println("extension: "+sd.getVersionedUrl());
    StructureDefinition existing = extensions.get(sd.getUrl());
    if (existing == null || !existing.hasVersion() || VersionUtilities.isThisOrLater(sd.getVersion(), existing.getVersion(), VersionUtilities.VersionPrecision.MINOR)) {
      extensions.put(sd.getUrl(), sd);
    }
  }

  public void finish(String target) throws FileNotFoundException, IOException {
    PrintStream p = new PrintStream(new FileOutputStream(Utilities.path(target, "extensions.txt")));

    List<String> entries = new ArrayList<>();
    Set<String> contexts = new HashSet<>();
    Set<String> contextGroups = new HashSet<>();
    for (StructureDefinition sd : extensions.values()) {
//      if (!Utilities.startsWithInList(sd.getUrl(), "", ""))
      for (StructureDefinitionContextComponent ctxt : sd.getContext()) {
        contexts.add(genExtensionContextName(ctxt));
      }
    }
    for (String s : contexts) {
      String g = s.contains(".") ? s.substring(0, s.indexOf(".")) : s;
      contextGroups.add(g);
    }
    for (String g : Utilities.sorted(contextGroups)) {
      StringBuilder b1 = header("Extension Analysis for context "+g);
      b1.append("<table class=\"grid\">\r\n");
      b1.append("<tr><th>Source</th><th>URL</th><th>Version</th><th>Realm</th><th>Type</th><th>Description</th></tr>\r\n");
      p.println("Context: "+g);
      Set<String> urls = new HashSet<>();
      String lastContext = null;
      for (String s : Utilities.sorted(contexts)) {
        if (s.equals(g) || s.startsWith(g+".")) {
          List<StructureDefinition> exts = new ArrayList<>();
          for (StructureDefinition sd : extensions.values()) {
            if (hasContext(sd, s)) {
              exts.add(sd);
            }
          }
          Collections.sort(exts, new CanonicalResourceSortByUrl());
          for (StructureDefinition sd : exts) {
            String c = s.substring(s.indexOf("-")+1);
            if (!c.equals(lastContext)) {
              b1.append("<tr><td colspan=\"6\" style=\"background-color: #eeeeee\"><b>"+c+"</b></td></tr>\r\n");
              lastContext = c;
            }
            b1.append("<tr><td>"+sd.getUserString("pid")+"</td><td><a href=\""+sd.getWebPath()+"\">"+sd.getVersionedUrl()+"</a></td><td>"+sd.getFhirVersion().toCode()+"</td><td>"+authority(sd)+"</td><td>"+typeSummary(sd)+"</td><td>"+Utilities.escapeXml(sd.getDescription())+uses(sd.getUrl())+"</td></tr>\r\n");
            p.println(s.substring(s.indexOf("-")+1)+"\t"+sd.getVersionedUrl()+"\t"+sd.getWebPath()+"\t"+authority(sd)+"\t"+sd.getFhirVersion().toCode()+"\t"+typeSummary(sd)+"\t"+sd.getDescription());
            urls.add(sd.getVersionedUrl());
          }
        }
      }
      b1.append("</table>\r\n"+footer());
      FileUtilities.stringToFile(b1.toString(), Utilities.path(target, "extension-summary-"+g.toLowerCase()+".html"));
      entries.add("<li><a href=\"extension-summary-"+g.toLowerCase()+".html\">"+g+"</a>: "+urls.size()+" extensions</li>\r\n");
      p.println("");
    }
    StringBuilder b = header("Extension Analysis");
    b.append("<ul>\r\n");
    for (String s : Utilities.sorted(entries)) {
      b.append(s);
    }
    b.append("</ul>\r\n");
    b.append("</table>\r\n"+footer());
    FileUtilities.stringToFile(b.toString(), Utilities.path(target, "extension-summary-analysis.html"));
  }

  private String uses(String url) {
    Set<String> paths = extensionUsage.get(url);
    if (paths == null) {
      return "";
    } else {
      return "<br/><br/><b>Profile Paths</b>: "+String.join(", ", Utilities.sorted(paths));
    }
  }

  private String genExtensionContextName(StructureDefinitionContextComponent ctxt) {
    switch (ctxt.getType()) {
    case ELEMENT:
      return ctxt.getExpression();
    case EXTENSION:
      return "Extension-"+Utilities.tail(ctxt.getExpression());
    case FHIRPATH:
      return "FHIRPath";
    default:
      return ctxt.getExpression();
    }
  }

  private StringBuilder header(String title) {
    StringBuilder b = new StringBuilder();
    b.append("<html>\r\n"+
        "<head>\r\n"+
        "<title>"+title+"</title>\r\n"+
        "<link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
        "</head>\r\n"+
        "<body style=\"margin: 10; background-color: white\">\r\n"+
        "<h2>"+title+"</h2>\r\n");
    return b;
  }

  private String footer() {
    return "</body></html>\r\n";
  }

  private String authority(StructureDefinition sd) {
    if (sd.hasUserData("realm")) {
      return sd.getUserString("realm");
    } else {
      String s = sd.hasJurisdiction() ? JurisdictionUtilities.displayJurisdictionShort(sd.getJurisdictionFirstRep().getCodingFirstRep()) : "?";
      if (s.contains("Unknown Jurisdiction")) {
        return "??";
      } else {
        return s;
      }
    }
  }

  private String typeSummary(StructureDefinition sd) {
    if (ProfileUtilities.isSimpleExtension(sd)) {
      return sd.getSnapshot().getElementByPath("Extension.value").typeSummary();
    } else {
      return "(complex)";
    }
  }

  private boolean hasContext(StructureDefinition sd, String s) {

    for (StructureDefinitionContextComponent ctxt : sd.getContext()) {
      if (s.equals(genExtensionContextName(ctxt))) {
        return true;
      }
    }
    return false;
  }

  public void seeUse(String url, String path, String path2) {
    if (path == null || Utilities.charCount(path, '.') == Utilities.charCount(path2, '.')) {
      path = path2;
    }
    if (path != null) {
      if (!extensionUsage.containsKey(url)) {
        extensionUsage.put(url, new HashSet<>());
      }
      extensionUsage.get(url).add(path);
    }
  }

}
