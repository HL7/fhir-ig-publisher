package org.hl7.fhir.igtools.renderers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;

public class HTAAnalysisRenderer {


  public class CopyRightUsageAnalysis {

    public int errs;
    public int warnings;
    public int suggestions;
    public String notes;
    public CanonicalResource cr;
    public boolean ok;
    public CopyRightUsageAnalysis() {
      super();
      notes = "";
    }
  }

  public class CopyRightAnalysis {

    public String url;
    public String name;
    public List<String> copyrights;
    public Boolean okToUse;
//    public int count;
//    public int c;
//    public int cok;
    public List<CopyRightUsageAnalysis> usages = new ArrayList<>();

//    public String report() {
//      StringBuilder b = new StringBuilder();
//      b.append("<ul>\r\n");
//      if (copyright != null) {
//        if ("".equals(copyright)) {
//          b.append(" <li>These resources should not have a copyright statements</li>\r\n"); 
//          b.append(" <li>"+c+" resources have Copyright Statements</li>\r\n"); 
//        } else if ("".equals(copyright)) {
//          b.append(" <li>These resources should have a copyright statement</li>\r\n"); 
//          b.append(" <li>"+c+" resources have Copyright Statements</li>\r\n"); 
//        } else {
//          b.append(" <li>These resources should have the copyright statement: <blockquote>"+Utilities.escapeXml(copyright)+"</blockquote></li>\r\n"); 
//          b.append(" <li>"+c+" resources have Copyright Statements</li>\r\n");           
//          b.append(" <li>"+cok+" resources have the correct Copyright Statements</li>\r\n");           
//        }
//      } else {
//        b.append(" <li>"+c+" resources have Copyright Statements</li>\r\n"); 
//      }
//      b.append("</ul>\r\n");
//      return b.toString();      
//    }
    
    public String summary() {
      if (okToUse == null) {
        return "To be resolved";
      }
      if (!okToUse) {
        return "This code system is not approved by HTA for use in an HL7 implementation Guide";
      }
      for (CopyRightUsageAnalysis usage : usages) {
        if (!usage.ok) {
          return "Some uses are missing the appropriate copyright statement";
        }
      }
      return "All OK";
    }

  }

  private IWorkerContext context;
  private String outputDir;
  private MarkDownProcessor md;

  public HTAAnalysisRenderer(IWorkerContext context, String outputDir, MarkDownProcessor md) {
   this.context = context;
   this.outputDir = outputDir;
   this.md = md;
  }

  public String render(String npmId, List<FetchedFile> fileList, String title) throws IOException {
    if ("hl7.terminology".equals(npmId)) {
      return "<i>UTG is exempt from terminology dependency analysis</i>";
    }
    if (npmId == null || !npmId.startsWith("hl7.")) {
      return "<i>Non-HL7 Igs are exempt from terminology dependency analysis</i>";
    }
    List<CopyRightAnalysis> analyses = processDependencies(fileList);
    
    if (analyses.isEmpty()) {
      return "<i>no Non-HL7 references found</i>";
    }
    return buildOutput(title, analyses);    
  }

  private String buildOutput(String title, List<CopyRightAnalysis> analyses) throws IOException {
    try {
      StringBuilder b = new StringBuilder();
      int errs = 0 ;
      int warnings = 0;
      int suggestions = 0;
      b.append("<ul>\r\n");
      int i = 0;
      for (CopyRightAnalysis analysis : analyses) {
        b.append(" <li><a href=\"#cs"+i+"\">"+analysis.url+"</a>: "+analysis.summary()+"</li>\r\n");      
        i++;
      }
      b.append("</ul>\r\n");

      i = 0;
      for (CopyRightAnalysis analysis : analyses) {
        b.append("<h2>"+analysis.url+(analysis.name == null ? "" : " ("+analysis.name+")")+"</h2><a name=\"cs"+i+"\">\r\n");
        b.append("<table class=\"grid\">\r\n");
        b.append(" <tr><td>Uses</td><td>"+analysis.usages.size()+"</td></tr>\r\n");
        b.append(" <tr><td>Expected Copyright</td><td>");
        if (analysis.copyrights.size() == 0) {
          b.append(presentCopyright(null));
        } else if (analysis.copyrights.size() == 1) {          
          b.append(presentCopyright(analysis.copyrights.get(0)));
        } else {
          b.append("<ul>");
          for (String s : analysis.copyrights) {
            b.append("<li>");
            b.append(presentCopyright(s));
            b.append("</li>");            
          }
          b.append("</ul>");
        }
        b.append("</td></tr>\r\n");
        b.append(" <tr><td>Approved:</td><td>"+analysis.summary()+"</td></tr>\r\n");
        b.append("</table>\r\n");
        b.append("<p></p>\r\n");

        b.append("<table class=\"grid\">\r\n");
        b.append(" <tr>\r\n");
        b.append("  <td>URL</td>\r\n");
        b.append("  <td>Name</td>\r\n");
        b.append("  <td>Copyright</td>\r\n");
        b.append("  <td>Notes</td>\r\n");
        b.append(" </tr>\r\n");

        for (CopyRightUsageAnalysis usage : analysis.usages) {
          errs = errs + usage.errs;
          warnings = warnings + usage.warnings;
          suggestions = suggestions + usage.suggestions;
          b.append(" <tr>\r\n");
          if (usage.cr.hasWebPath()) {
            b.append("  <td><a href=\""+usage.cr.getWebPath()+"\">"+tail(usage.cr.getUrl())+"</a></td>\r\n");
          } else {
            b.append("  <td>"+tail(usage.cr.getUrl())+"</td>\r\n");
          }
          b.append("  <td>"+usage.cr.present()+"</td>\r\n");
          b.append("  <td>"+presentUsageCopyright(analysis.copyrights, usage.cr.getCopyright())+"</td>\r\n");
          b.append("  <td>"+usage.notes+"</td>\r\n");
          b.append(" </tr>\r\n");
        }
        b.append("</table>\r\n");
        i++;
      }
      String cnt = TEMPLATE;
      cnt = cnt.replace("$title$", Utilities.escapeXml(title));
      cnt = cnt.replace("$analysis$", b.toString());
      FileUtilities.stringToFile(cnt, Utilities.path(outputDir, "qa-hta.html"));

      if (errs + warnings + suggestions == 0) {
        return "<a href=\"qa-hta.html\">All OK</a>";
      } else if (errs + warnings == 0) {
        return "<a href=\"qa-hta.html\">All OK, "+suggestions+" "+Utilities.pluralize("suggestion", suggestions)+"</a>";
      } else {
        return "<a href=\"qa-hta.html\"><span style=\"background-color: #ffcccc\">"+(errs+warnings)+" "+Utilities.pluralize("issue", errs+warnings)+"</span></a>";
      }
    } catch (Exception e) {
      return "Exception generating HTA Analysis: "+e.getMessage();
    }
  }

  private String tail(String url) {
    return url.contains("/") ? url.substring(url.lastIndexOf("/")+1) : url;
  }

  private String presentUsageCopyright(List<String> copyrights, String ucopy) {
    boolean ok = false;
    for (String copy : copyrights) {
      ok = ok || (ucopy != null && copy != null && ucopy.contains(copy));
    }
    if (ok) {
      return "<span style=\"font-size: 14; background-color: #afffad\">&#x2611;</span>";
    } else if (!Utilities.noString(ucopy)) {
      return md.process(ucopy, "resource copyright");
    } else {
      return "";
    }
  }

  private String presentCopyright(String copyright) {
    if (copyright == null) {
      return "<i>(copyright expectations unknown)</i>";
    }
    if (copyright.equals("")) {
      return "<i>This code system should not have a copyright</i>";
    }
    if (copyright.equals("*")) {
      return "<i>This code system should have a copyright, but no particular wording is required</i>";
    }
    return md.process(copyright, "expected copyright");
  }

//  private CopyRightAnalysis analyseCopyright(String url, List<CanonicalResource> list) {
//    CopyRightAnalysis res = new CopyRightAnalysis();
//    res.copyright = getApprovedCopyright(url);
//    res.okToUse = getIsApproved(url);
//    for (CanonicalResource cr : list) {
//      res.count++;
//      if (cr.hasCopyright()) {
//        res.c++;
//      }
//      if (res.copyright != null) {
//        if (res.copyright.equals("")) {
//          if (!cr.hasCopyright()) {
//            res.cok++;
//          }
//        } else if (res.copyright.equals("*")) {
//          if (cr.hasCopyright()) {
//            res.cok++;
//          }
//        } else if (cr.hasCopyright() && cr.getCopyright().matches(res.copyright)) {
//          res.cok++;
//        }
//      } else {
//        res.cok++;
//      }
//    }
//    return res;
//  }

  private Boolean getIsApproved(String system) {
    if (Utilities.existsInList(system, "http://snomed.info/sct", "http://loinc.org")) {
      return true;
    }
    return null;
  }

  // null: we don't know
  // "" - it should be empty 
  // "*" - it should be something
  // else the actual pattern for the copyright
  private List<String> getApprovedCopyright(String url) {
    List<String> res = new ArrayList<>();
    if ("http://snomed.info/sct".equals(url)) {
      res.add("This value set includes content from SNOMED CT, which is copyright © 2002+ International Health Terminology Standards Development Organisation (IHTSDO), and distributed by agreement between IHTSDO and HL7. Implementer use of SNOMED CT is not covered by this agreement");
      res.add("The SNOMED International IPS Terminology is distributed by International Health Terminology Standards Development Organisation, trading as SNOMED International, and is subject the terms of the [Creative Commons Attribution 4.0 International Public License](https://creativecommons.org/licenses/by/4.0/). For more information, see [SNOMED IPS Terminology](https://www.snomed.org/snomed-ct/Other-SNOMED-products/international-patient-summary-terminology)");
      res.add("The HL7 International IPS implementation guides incorporate SNOMED CT®, used by permission of the International Health Terminology Standards Development Organisation, trading as SNOMED International. SNOMED CT was originally created by the College of American Pathologists. SNOMED CT is a registered trademark of the International Health Terminology Standards Development Organisation, all rights reserved. Implementers of SNOMED CT should review [usage terms](https://www.snomed.org/get-snomed) or directly contact SNOMED International: info@snomed.org");
    }
    if ("http://loinc.org".equals(url)) {
      res.add("This material contains content from LOINC (http://loinc.org). LOINC is copyright © 1995-2020, Regenstrief Institute, Inc. and the Logical Observation Identifiers Names and Codes (LOINC) Committee and is available at no cost under the license at http://loinc.org/license. LOINC® is a registered United States trademark of Regenstrief Institute, Inc");
    }
    if ("http://www.ama-assn.org/go/cpt".equals(url)) {
      res.add("Current Procedural Terminology (CPT) is copyright 2020 American Medical Association. All rights reserved");
    }
    if ("http://www.whocc.no/atc".equals(url)) {
      res.add("This artifact includes content from Anatomical Therapeutic Chemical (ATC) classification system. ATC codes are copyright World Health Organization (WHO) Collaborating Centre for Drug Statistics Methodology. Terms & Conditions in https://www.whocc.no/use_of_atc_ddd/");
    }
    if ("urn:oid:2.16.840.1.113883.2.9.6.2.7".equals(url)) {
      res.add("This artifact includes content from International Standard Classification of Occupations (ISCO). ISCO is copyright International Labour Organization (ILO). Terms & Conditions in http://www.ilo.org/global/copyright/lang--en/index.htm");
    }
    if ("http://standardterms.edqm.eu".equals(url)) {
      res.add("This artifact includes content from EDQM Standard Terms. EDQM Standard Terms are copyright European Directorate for the Quality of Medicines. Terms & Conditions in https://www.edqm.eu/en/standard-terms-database");
    }
    return res;
  }

  private List<CopyRightAnalysis> processDependencies(List<FetchedFile> fileList) {
    Set<String> internal = new HashSet<>(); 
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof CodeSystem) {
          internal.add(((CodeSystem) r.getResource()).getUrl());
        }
      }
    }
    List<CopyRightAnalysis> res = new ArrayList<>();
    for (FetchedFile f : fileList) {
      for (FetchedResource r : f.getResources()) {
        if (r.getResource() != null && r.getResource() instanceof ValueSet) {
          ValueSet vs = (ValueSet) r.getResource();
          processValueSet(internal, res, vs);
        }
        if (r.getResource() != null && r.getResource() instanceof ConceptMap) {
          ConceptMap cm = (ConceptMap) r.getResource();
          processConceptMap(internal, res, cm);
        }
      }
    }
    
    return res;
  }

  private void processConceptMap(Set<String> internal, List<CopyRightAnalysis> res, ConceptMap cm) {
    
  }

  private void processValueSet(Set<String> internal, List<CopyRightAnalysis> res, ValueSet vs) {
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      processInclude(internal, res, inc, vs);
    }
//    for (ConceptSetComponent inc : vs.getCompose().getExclude()) {
//      processInclude(internal, res, inc, vs);
//    }    
  }

  private void processInclude(Set<String> internal, List<CopyRightAnalysis> res, ConceptSetComponent inc, CanonicalResource vs) {
    if (inc.hasSystem()) {
      if (!internal.contains(inc.getSystem()) && !isHL7System(inc.getSystem())) {
        CopyRightAnalysis analysis = getAnalysis(res, inc.getSystem());
        CopyRightUsageAnalysis usage = new CopyRightUsageAnalysis();
        analysis.usages.add(usage);
        usage.cr = vs;

        if (!analysis.copyrights.isEmpty()) {
          if (analysis.copyrights.size() == 1 && analysis.copyrights.get(0).equals("")) {
            if (!vs.hasCopyright()) {
              usage.ok = true;
            } else {
              usage.errs++;
              usage.notes = usage.notes + "<li style=\"background-color: #fbccfc\">This Value Set should not have a copyright statement</li>\r\n";
            }
          } else if (analysis.copyrights.size() == 1 && analysis.copyrights.get(0).equals("*")) {
            if (vs.hasCopyright()) {
              usage.ok = true;
            } else {
              usage.errs++;
              usage.notes = usage.notes + "<li style=\"background-color: #fcf0cc\">This Value Set should have a copyright statement</li>\r\n";
            }
          } else {
            boolean ok = false;
            for (String copy : analysis.copyrights) {
              ok = ok || vs.hasCopyright() && vs.getCopyright().contains(copy);
            }
            if (ok) {
              usage.ok = true;
            } else {
              usage.errs++;
              if (!vs.hasCopyright()) {
                usage.notes = usage.notes + "<li style=\"background-color: #fcf0cc\">The copyright statement is missing</li>\r\n";            
              } else {
                usage.notes = usage.notes + "<li style=\"background-color: #fccccc\">The copyright statement is wrong</li>\r\n";            
              }
            }
          }
        } else {
          usage.ok = true;
        }
      }
    } 
  }

  private CopyRightAnalysis getAnalysis(List<CopyRightAnalysis> res, String system) {
    for (CopyRightAnalysis t : res) {
      if (t.url.equals(system)) {
        return t;
      }
    }
    CopyRightAnalysis analysis = new CopyRightAnalysis();
    analysis.url = system;
    analysis.name = name(system);
    analysis.copyrights = getApprovedCopyright(system);
    analysis.okToUse = getIsApproved(system);
    res.add(analysis);
    return analysis;
  }

  private boolean isHL7System(String system) {
    if (system.startsWith("http://terminology.hl7.org") || system.startsWith("http://hl7.org/fhir")) {
      return true;
    }
//    if (Utilities.existsInList(system, ""))
    return false;
  }

  private String name(String url) {
    CodeSystem cs = context.fetchCodeSystem(url);
    if (cs != null) {
      return cs.present();
    }
    return null;
  }

  private final String TEMPLATE = 
      "<!DOCTYPE HTML>\r\n"+
      "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\r\n"+
      "<head>\r\n"+
      "  <title>$title$ : Validation Results</title>\r\n"+
      "  <link href=\"fhir.css\" rel=\"stylesheet\"/>\r\n"+
      "</head>\r\n"+
      "<body style=\"margin: 20px; background-color: #ffffff\">\r\n"+
      "<h1>HTA License Conformance Analysis for $title$</h1>\r\n"+
      "<p style=\"background-color: #ffcccc; border:1px solid grey; padding: 5px; max-width: 790px;\">\r\n"+
      "The content of this page is being developed with the HTA committee, and is subject to further change. Editors are welcome to use this page to pick up errors in their definitions, but should not regard the analysis as final.\r\n"+
      "</p>\r\n"+
      "$analysis$"+
      "</body>\r\n"+
      "</html>\r\n";
  
}
