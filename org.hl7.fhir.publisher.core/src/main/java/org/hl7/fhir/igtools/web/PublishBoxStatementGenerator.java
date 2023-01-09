package org.hl7.fhir.igtools.web;

import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class PublishBoxStatementGenerator {
  
  /**
   * The fragment of HTML this generates has 3 parts 
   * 
   * 1. statement of what this is 
   * 2. reference to current version
   * 3. referenceto list of published versions
   * 
   * @param version
   * @param root
   * @param canonical
   * @return
   */
  public static String genFragment(PackageList ig, PackageListEntry version, PackageListEntry root, String canonical, boolean currentPublication, boolean isCore) {
    String p1 = ig.title()+" (v"+version.version()+": "+state(ig, version)+")";
    if (!isCore) {
      p1 = p1 + (version.fhirVersion() != null ? " based on <a href=\"http://hl7.org/fhir/"+getPath(version.fhirVersion())+"\">FHIR "+fhirRef(version.fhirVersion())+"</a>" : "")+". ";
    } else {
      p1 = p1 + ". ";      
    }
    String p2 = root == null ? "" : version == root ? "This is the current published version"+(currentPublication ? "" : " in its permanent home (it will always be available at this URL)") :
      "The current version which supercedes this version is <a href=\""+(root.path().startsWith(canonical) ? canonical : root.path())+"{{fn}}\">"+root.version()+"</a>";
    String p3;
    if (canonical.equals("http://hl7.org/fhir"))
      p3 = " For a full list of available versions, see the <a href=\""+canonical+"/directory.html\">Directory of published versions <img src=\"external.png\" style=\"text-align: baseline\"></a>";
    else
      p3 = " For a full list of available versions, see the <a href=\""+canonical+"/history.html\">Directory of published versions <img src=\"external.png\" style=\"text-align: baseline\"></a>";
    return "This page is part of the "+p1+p2+". "+p3;
  }

  
  private static String getPath(String v) {
    if ("4.0.1".equals(v))
      return "R4";
    if ("4.0.0".equals(v))
      return "R4";
    if ("3.5a.0".equals(v))
      return "2018Dec";
    if ("3.5.0".equals(v))
      return "2018Sep";
    if ("3.3.0".equals(v))
      return "2018May";
    if ("3.2.0".equals(v))
      return "2018Jan";
    if ("3.0.0".equals(v))
      return "STU3";
    if ("3.0.1".equals(v))
      return "STU3";
    if ("3.0.2".equals(v))
      return "STU3";
    if ("1.8.0".equals(v))
      return "2017Jan";
    if ("1.6.0".equals(v))
      return "2016Sep";
    if ("1.4.0".equals(v))
      return "2016May";
    if ("1.1.0".equals(v))
      return "2015Dec";
    if ("1.0.2".equals(v))
      return "DSTU2";
    if ("1.0.0".equals(v))
      return "2015Sep";
    if ("0.5.0".equals(v))
      return "2015May";
    if ("0.4.0".equals(v))
      return "2015Jan";
    if ("0.0.82".equals(v))
      return "DSTU1";
    if ("0.11".equals(v))
      return "2013Sep";
    if ("0.06".equals(v))
      return "2013Jan";
    if ("0.05".equals(v))
      return "2012Sep";
    if ("0.01".equals(v))
      return "2012May";
    if ("current".equals(v))
      return "2011Aug";
    return v;
  }

  private static String fhirRef(String v) {
    if (VersionUtilities.isR2Ver(v))
      return "R2";
    if (VersionUtilities.isR3Ver(v))
      return "R3";
    if (VersionUtilities.isR4Ver(v))
      return "R4";    
    return "v"+v;
  }

  private static String state(PackageList ig, PackageListEntry version) {
    String status = version.status();
    String sequence = version.sequence();
    if ("trial-use".equals(status))
      return decorate(sequence);
    else if ("release".equals(status))
      return "Release";
    else if ("qa-preview".equals(status))
      return "QA Preview";
    else if ("ballot".equals(status))
      return decorate(sequence)+" Ballot "+ballotCount(ig, sequence, version);
    else if ("public-comment".equals(status))
      return decorate(sequence)+" Public Comment";
    else if ("draft".equals(status))
      return decorate(sequence)+" Draft";
    else if ("update".equals(status))
      return decorate(sequence)+" Update";
    else if ("normative+trial-use".equals(status))
      return decorate(sequence+" - Mixed Normative and STU");
    else if ("normative".equals(status))
      return decorate(sequence+" - Normative");
    else if ("informative".equals(status))
      return decorate(sequence+" - Informative");
    else 
      throw new Error("unknown status "+status);
  }

  private static String decorate(String sequence) {
    sequence = sequence.replace("Normative", "<a href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Normative Standard\">Normative</a>");
    if (sequence.contains("DSTU"))
      return sequence.replace("DSTU", "<a href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Draft Standard for Trial-Use\">DSTU</a>");
    else
      return sequence.replace("STU", "<a href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Standard for Trial-Use\">STU</a>");
  }

  private static String ballotCount(PackageList ig, String sequence, PackageListEntry version) {
    int c = 1;
    for (int i = ig.list().size() - 1; i >= 0; i--) {
      PackageListEntry o = ig.list().get(i);
      if (o == version)
        return Integer.toString(c);
      if (sequence.equals(o.sequence()) && "ballot".equals(version.status()))
        c++;
    }
    return "1";
  }

}
