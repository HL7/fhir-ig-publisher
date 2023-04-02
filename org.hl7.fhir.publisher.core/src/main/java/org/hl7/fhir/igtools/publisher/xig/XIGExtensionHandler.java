package org.hl7.fhir.igtools.publisher.xig;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.terminologies.JurisdictionUtilities;
import org.hl7.fhir.r5.utils.ResourceSorters.CanonicalResourceSortByUrl;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;

public class XIGExtensionHandler {

  private Map<String, StructureDefinition> extensions = new HashMap<>();
  
  public void seeExtension(StructureDefinition sd) {
//    System.out.println("extension: "+sd.getVersionedUrl());
    StructureDefinition existing = extensions.get(sd.getUrl());
    if (existing == null || !existing.hasVersion() || VersionUtilities.isThisOrLater(sd.getVersion(), existing.getVersion())) {
      extensions.put(sd.getUrl(), sd);
    }
  }

  public void finish(String target) throws FileNotFoundException, IOException {
    PrintStream p = new PrintStream(new FileOutputStream(Utilities.path(target, "extensions.txt")));
    StringBuilder b = header("Extension Analysis");
    b.append("<ul>\r\n");

    Set<String> contexts = new HashSet<>();
    Set<String> contextGroups = new HashSet<>();
    for (StructureDefinition sd : extensions.values()) {
      for (StructureDefinitionContextComponent ctxt : sd.getContext()) {
        contexts.add(genExtensionContextName(ctxt));
      }
    }
    for (String s : contexts) {
      String g = s.contains(".") ? s.substring(0, s.indexOf(".")) : s;
      contextGroups.add(g);
    }
    for (String g : contextGroups) {
      StringBuilder b1 = header("Extension Analysis for context "+g);
      b1.append("<table class=\"grid\">\r\n");
      b1.append("<tr><th>Context</th><th>URL</th><th>Version</th><th>Realm</th><th>Type</th><th>Description</th></tr>\r\n");
      p.println("Context: "+g);
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
            b1.append("<tr><td>"+s.substring(s.indexOf("-")+1)+"</td><td><a href=\""+sd.getWebPath()+"\">"+sd.getVersionedUrl()+"</a></td><td>"+sd.getFhirVersion().toCode()+"</td><td>"+authority(sd)+"</td><td>"+typeSummary(sd)+"</td><td>"+Utilities.escapeXml(sd.getDescription())+"</td></tr>\r\n");
            p.println(s.substring(s.indexOf("-")+1)+"\t"+sd.getVersionedUrl()+"\t"+sd.getWebPath()+"\t"+authority(sd)+"\t"+sd.getFhirVersion().toCode()+"\t"+typeSummary(sd)+"\t"+sd.getDescription());
          }
        }
      }
      b1.append("</table>\r\n"+footer());
      TextFile.stringToFile(b1.toString(), Utilities.path(target, "extension-summary-"+g.toLowerCase()+".html"));
      b.append("<li><a href=\"extension-summary-"+g.toLowerCase()+".html\">"+g+"</a></li>\r\n");
      p.println("");
    }
    b.append("</ul>\r\n");
    b.append("</table>\r\n"+footer());
    TextFile.stringToFile(b.toString(), Utilities.path(target, "extension-summary-analysis.html"));
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
    return sd.hasJurisdiction() ? JurisdictionUtilities.displayJurisdiction(sd.getJurisdictionFirstRep().getCodingFirstRep()) : "?";
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

}
