package org.hl7.fhir.igtools.publisher.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonElement;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.parser.JsonParser;


public class HL7OrgFhirPhpFixer {

  public static void main(String[] args) throws IOException {
    File folderRoot = new File("/Users/grahamegrieve/web/www.hl7.org.cda");
    new HL7OrgFhirPhpFixer().execute(folderRoot, folderRoot, true);
  }

  private void execute(File rootFolder, File folder, boolean root) throws IOException {
    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        if (root) {
          System.out.println("Process "+f.getAbsolutePath());
        }
        execute(rootFolder, f, false);
      } else if ("index.php".equals(f.getName())) {
        processPhp(rootFolder, f);
      }
    }
  }

  private void processPhp(File rootFolder, File f) throws FileNotFoundException, IOException {
    
    String[] lines = FileUtilities.fileToLines(f); 
    boolean changed = false;
    int x = findLine(lines, "elseif (strpos($accept, 'xml')");
    int h = findLine(lines, "elseif (strpos($accept, 'html')");
    if (x == h - 2) {
      swap(lines, x, h);
      swap(lines, x+1, h+1);
      changed = true;
    } 
    
    for (int i = 0; i < lines.length; i++) {
      String l = lines[i];
      if (l.contains("Redirect('")) {
        String url = l.substring(l.indexOf("'")+1);
        url = url.substring(0, url.indexOf("'"));
        if (url.startsWith("/fhir")) {
          url = url.substring(5);
        }
        File fu = new File(Utilities.path(rootFolder, url));
        if (!fu.exists()) {
          String u = url;
          if (u.endsWith(".profile.html")) {
            u = u.replace(".profile.html", ".html");
          } 
          fu = new File(Utilities.path(rootFolder, u));
          if (fu.exists()) {
            lines[i] = l.replace(url, u);
            changed = true;
          } else {
            String cf = getCorrectFile(url.substring(url.lastIndexOf("/")+1));
            if (cf != null) {
              u = url.replace(url.substring(url.lastIndexOf("/")+1), cf);
              fu = new File(Utilities.path(rootFolder, u.contains("#") ? u.substring(0, u.indexOf("#")) : u));
              if (fu.exists()) {
                lines[i] = l.replace(url, u);
                changed = true;
              } 
            }
          }
        }
        if (!fu.exists()) {
          System.out.println("File "+fu.getAbsolutePath()+" not found in "+f.getAbsolutePath());
        }
      }
    }
    if (changed) {
      FileUtilities.linesToFile(f.getAbsolutePath(), lines);
    }
  }

  private String getCorrectFile(String fn) {
    switch (fn) {
    case "address.html" : return "datatypes.html#Address";
    case "age.html" : return "datatypes.html#Age";
    case "annotation.html" : return "datatypes.html#Annotation";
    case "attachment.html" : return "datatypes.html#Attachment";
    case "backbonetype.html" : return "types.html#BackboneType";
    case "base.html" : return "types.html#Base";
    case "base64binary.html" : return "datatypes.html#base6Binary";
    case "boolean.html" : return "datatypes.html#boolean";
    case "canonical.html" : return "datatypes.html#canonical";
    case "code.html" : return "datatypes.html#code";
    case "codeableconcept.html" : return "datatypes.html#CodeableConcept";
    case "codeablereference.html" : return "references.html#CodeableReference";
    case "coding.html" : return "datatypes.html#Coding";
    case "contactdetail.html" : return "metadatatypes.html#ContactDetail";
    case "contactpoint.html" : return "datatypes.html#ContactPoint";
    case "contributor.html" : return "metadatatypes.html#Contributor";
    case "count.html" : return "datatypes.html#Count";
    case "datarequirement.html" : return "metadatatypes.html#DataRequirement";
    case "datatype.html" : return "types.html#DataType";
    case "date.html" : return "datatypes.html#date";
    case "datetime.html" : return "datatypes.html#dateTime";
    case "decimal.html" : return "datatypes.html#decimal";
    case "distance.html" : return "datatypes.html#Distance";
    case "duration.html" : return "datatypes.html#Duration";
    case "expression.html" : return "metadatatypes.html#Expression";
    case "extension.html" : return "extensibility.html#Extension";
    case "humanname.html" : return "datatypes.html#HumanName";
    case "id.html" : return "datatypes.html#id";
    case "identifier.html" : return "datatypes.html#Identifier";
    case "instant.html" : return "datatypes.html#instant";
    case "integer.html" : return "datatypes.html#integer";
    case "integer64.html" : return "datatypes.html#integer64";
    case "markdown.html" : return "datatypes.html#markdown";
    case "meta.html" : return "resource.html#Meta";
    case "money.html" : return "datatypes.html#Money";
    case "moneyquantity.html" : return "datatypes.html#MoneyQuantity";
    case "oid.html" : return "datatypes.html#oid";
    case "parameterdefinition.html" : return "metadatatypes.html#ParameterDefinition";
    case "period.html" : return "datatypes.html#Period";
    case "positiveint.html" : return "datatypes.html#positiveInt";
    case "primitivetype.html" : return "types.html#primitiveType";
    case "quantity.html" : return "datatypes.html#Quantity";
    case "range.html" : return "datatypes.html#Range";
    case "ratio.html" : return "datatypes.html#Ratio";
    case "ratiorange.html" : return "datatypes.html#RatioRange";
    case "reference.html" : return "references.html#Reference";
    case "relatedartifact.html" : return "metadatatypes.html#RelatedArtifact";
    case "sampleddata.html" : return "datatypes.html#SampledData";
    case "signature.html" : return "datatypes.html#Signature";
    case "simplequantity.html" : return "datatypes.html#SimpleQuantity";
    case "string.html" : return "datatypes.html#string";
    case "time.html" : return "datatypes.html#time";
    case "timing.html" : return "datatypes.html#Timing";
    case "triggerdefinition.html" : return "metadatatypes.html#TriggerDefinition";
    case "unsignedint.html" : return "datatypes.html#unsignedInt";
    case "uri.html" : return "datatypes.html#uri";
    case "url.html" : return "datatypes.html#url";
    case "usagecontext.html" : return "metadatatypes.html#UsageContext";
    case "uuid.html" : return "datatypes.html#uuid";
    case "xhtml.html" : return "narrative.html#xhtml";
    case "address.profile.html" : return "datatypes.html#Address";
    case "age.profile.html" : return "datatypes.html#Age";
    case "annotation.profile.html" : return "datatypes.html#Annotation";
    case "attachment.profile.html" : return "datatypes.html#Attachment";
    case "backbonetype.profile.html" : return "types.html#BackboneType";
    case "base.profile.html" : return "types.html#Base";
    case "base64binary.profile.html" : return "datatypes.html#base6Binary";
    case "boolean.profile.html" : return "datatypes.html#boolean";
    case "canonical.profile.html" : return "datatypes.html#canonical";
    case "code.profile.html" : return "datatypes.html#code";
    case "codeableconcept.profile.html" : return "datatypes.html#CodeableConcept";
    case "codeablereference.profile.html" : return "references.html#CodeableReference";
    case "coding.profile.html" : return "datatypes.html#Coding";
    case "contactdetail.profile.html" : return "metadatatypes.html#ContactDetail";
    case "contactpoint.profile.html" : return "datatypes.html#ContactPoint";
    case "contributor.profile.html" : return "metadatatypes.html#Contributor";
    case "count.profile.html" : return "datatypes.html#Count";
    case "datarequirement.profile.html" : return "metadatatypes.html#DataRequirement";
    case "datatype.profile.html" : return "types.html#DataType";
    case "date.profile.html" : return "datatypes.html#date";
    case "datetime.profile.html" : return "datatypes.html#dateTime";
    case "decimal.profile.html" : return "datatypes.html#decimal";
    case "distance.profile.html" : return "datatypes.html#Distance";
    case "duration.profile.html" : return "datatypes.html#Duration";
    case "expression.profile.html" : return "metadatatypes.html#Expression";
    case "extension.profile.html" : return "extensibility.html#Extension";
    case "humanname.profile.html" : return "datatypes.html#HumanName";
    case "id.profile.html" : return "datatypes.html#id";
    case "identifier.profile.html" : return "datatypes.html#Identifier";
    case "instant.profile.html" : return "datatypes.html#instant";
    case "integer.profile.html" : return "datatypes.html#integer";
    case "integer64.profile.html" : return "datatypes.html#integer64";
    case "markdown.profile.html" : return "datatypes.html#markdown";
    case "meta.profile.html" : return "resource.html#Meta";
    case "money.profile.html" : return "datatypes.html#Money";
    case "moneyquantity.profile.html" : return "datatypes.html#MoneyQuantity";
    case "oid.profile.html" : return "datatypes.html#oid";
    case "parameterdefinition.profile.html" : return "metadatatypes.html#ParameterDefinition";
    case "period.profile.html" : return "datatypes.html#Period";
    case "positiveint.profile.html" : return "datatypes.html#positiveInt";
    case "primitivetype.profile.html" : return "types.html#primitiveType";
    case "quantity.profile.html" : return "datatypes.html#Quantity";
    case "range.profile.html" : return "datatypes.html#Range";
    case "ratio.profile.html" : return "datatypes.html#Ratio";
    case "ratiorange.profile.html" : return "datatypes.html#RatioRange";
    case "reference.profile.html" : return "references.html#Reference";
    case "relatedartifact.profile.html" : return "metadatatypes.html#RelatedArtifact";
    case "sampleddata.profile.html" : return "datatypes.html#SampledData";
    case "signature.profile.html" : return "datatypes.html#Signature";
    case "simplequantity.profile.html" : return "datatypes.html#SimpleQuantity";
    case "string.profile.html" : return "datatypes.html#string";
    case "time.profile.html" : return "datatypes.html#time";
    case "timing.profile.html" : return "datatypes.html#Timing";
    case "triggerdefinition.profile.html" : return "metadatatypes.html#TriggerDefinition";
    case "unsignedint.profile.html" : return "datatypes.html#unsignedInt";
    case "uri.profile.html" : return "datatypes.html#uri";
    case "url.profile.html" : return "datatypes.html#url";
    case "usagecontext.profile.html" : return "metadatatypes.html#UsageContext";
    case "uuid.profile.html" : return "datatypes.html#uuid";
    case "xhtml.profile.html" : return "narrative.html#xhtml";

    default: return null;
    }

  }

  private int findLine(String[] lines, String fragment) {
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains(fragment)) {
        return i;
      }
    }
    return -1;
  }

  private void swap(String[] lines, int x, int h) {
    String s = lines[h];
    lines[h] = lines[x];
    lines[x] = s;
    
  }
}

