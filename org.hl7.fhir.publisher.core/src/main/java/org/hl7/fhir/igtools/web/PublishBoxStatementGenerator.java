package org.hl7.fhir.igtools.web;

import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.PackageList;
import org.hl7.fhir.utilities.npm.PackageList.PackageListEntry;

public class PublishBoxStatementGenerator {
  
  /**
   * The fragment of HTML this generates has 3 parts 
   * 
   * 1. statement of what this is 
   * 2. reference to current version
   * 3. reference to list of published versions
   * 
   * @param version
   * @param root
   * @param canonical
   * @return
   */
  public static String genFragment(PackageList ig, PackageListEntry version, PackageListEntry root, String canonical, boolean currentPublication, boolean isCore) {
    String p1, p2, p3; 
    if ("withdrawn".equals(version.status())) {
      p1 = ig.title()+" Withdrawal notice (v"+version.version()+": "+state(ig, version)+").";
      p2 = "";
      p3 = " For a full list of versions prior to withdrawal, see the <a data-no-external=\"true\" href=\""+canonical+"/history.html\">Directory of published versions</a>";
      return "This page is the "+p1+" "+p3;
    } else {
      p1 = ig.title()+" (v"+version.version()+": "+state(ig, version)+")";
      if (!isCore) {
        p1 = p1 + (version.fhirVersion() != null ? (isCDA(canonical) ? " generated with " : " based on ")+"<a data-no-external=\"true\" href=\"http://hl7.org/fhir/"+getPath(version.fhirVersion())+"\">FHIR (HL7® FHIR® Standard) "+fhirRef(version.fhirVersion())+"</a>" : "")+". ";
      } else {
        p1 = p1 + ". ";      
      }

      if (root == null) {
        p2 = "No current official version has been published yet";
      } else if (version == root) {
        p2 = "This is the current published version"+(currentPublication ? "" : " in its permanent home (it will always be available at this URL)");
      } else if ("withdrawn".equals(root.status())){
        p2 = "This specification was withdrawn after the publication of this version: see <a data-no-external=\"true\" href=\""+(root.path().startsWith(canonical) ? canonical : root.path())+"{{fn}}\">Withdrawal Notice</a>";
      } else if (VersionUtilities.compareVersions(root.version(), version.version()) > 0) {
        p2 = "The current version which supersedes this version is <a data-no-external=\"true\" href=\""+(root.path().startsWith(canonical) ? canonical : root.path())+"{{fn}}\">"+root.version()+"</a>";
      } else {
        p2 = "This version is a pre-release. The current official version is <a data-no-external=\"true\" href=\""+(root.path().startsWith(canonical) ? canonical : root.path())+"{{fn}}\">"+root.version()+"</a>";
      }
      if (canonical.equals("http://hl7.org/fhir")) {
        p3 = " For a full list of available versions, see the <a data-no-external=\"true\" href=\""+canonical+"/directory.html\">Directory of published versions</a>";
      } else if (root != null && "withdrawn".equals(root.status())) {
        p3 = " For a full list of versions prior to withdrawal, see the <a data-no-external=\"true\" href=\""+canonical+"/history.html\">Directory of published versions</a>";
      } else {
        p3 = " For a full list of available versions, see the <a data-no-external=\"true\" href=\""+canonical+"/history.html\">Directory of published versions</a>";
      }
      return "This page is part of the "+p1+p2+". "+p3;
    }
  }

  private static boolean isCDA(String canonical) {
    return canonical.startsWith("http://hl7.org/cda");
  }


  private static String getPath(String v) {
    if ("5.0.0".equals(v))
      return "R5";
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
    else if ("preview".equals(status) || "qa-preview".equals(status))
      return "QA Preview";
    else if ("ballot".equals(status)) {
      String bc = ballotCount(ig, sequence, version);
      if (Utilities.noString(bc)) {
        return decorate(sequence+" Ballot");
      } else {
        return decorate(sequence)+" Ballot "+bc;
      }
    } else if ("public-comment".equals(status))
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
    else if ("corrected".equals(status))
      return decorate(sequence+" - Replaced");
    else if ("withdrawn".equals(status))
      return decorate(sequence+" - Withdrawn");
    else 
      throw new Error("unknown status "+status);
  }

  private static String decorate(String sequence) {
    sequence = sequence.replace("Normative", "<a data-no-external=\"true\" href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Normative Standard\">Normative</a>");
    if (sequence.contains("DSTU"))
      return sequence.replace("DSTU", "<a data-no-external=\"true\" href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Draft Standard for Trial-Use\">DSTU</a>");
    else
      return sequence.replace("STU", "<a data-no-external=\"true\" href=\"https://confluence.hl7.org/display/HL7/HL7+Balloting\" title=\"Standard for Trial-Use\">STU</a>");
  }

  private static String ballotCount(PackageList ig, String sequence, PackageListEntry version) {
    int c = 1;
    for (int i = ig.list().size() - 1; i >= 0; i--) {
      PackageListEntry o = ig.list().get(i);
      if (o == version) {
        return c == 0 ? "" : Integer.toString(c);
      }
      if (Utilities.existsInListNC(o.status(), "trial-use", "normative")) {
        c = 0;
      }
      if (sequence.equals(o.sequence()) && "ballot".equals(o.status())) {
        c++;
      }
    }
    return "1";
  }

}
