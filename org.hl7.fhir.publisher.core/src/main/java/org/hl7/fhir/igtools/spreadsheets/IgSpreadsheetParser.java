package org.hl7.fhir.igtools.spreadsheets;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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


import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.r5.conformance.ProfileUtilities;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Base64BinaryType;
import org.hl7.fhir.r5.model.BooleanType;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Constants;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.DataType;
import org.hl7.fhir.r5.model.DateTimeType;
import org.hl7.fhir.r5.model.DateType;
import org.hl7.fhir.r5.model.DecimalType;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ConstraintSeverity;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionConstraintComponent;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionMappingComponent;
import org.hl7.fhir.r5.model.ElementDefinition.PropertyRepresentation;
import org.hl7.fhir.r5.model.ElementDefinition.SlicingRules;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Enumerations;
import org.hl7.fhir.r5.model.Enumerations.BindingStrength;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.Enumerations.QuantityComparator;
import org.hl7.fhir.r5.model.Enumerations.SearchParamType;
import org.hl7.fhir.r5.model.Extension;
import org.hl7.fhir.r5.model.Factory;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.InstantType;
import org.hl7.fhir.r5.model.IntegerType;
import org.hl7.fhir.r5.model.OidType;
import org.hl7.fhir.r5.model.OperationDefinition;
import org.hl7.fhir.r5.model.OperationDefinition.OperationDefinitionParameterComponent;
import org.hl7.fhir.r5.model.Period;
import org.hl7.fhir.r5.model.PositiveIntType;
import org.hl7.fhir.r5.model.Quantity;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.SearchParameter;
import org.hl7.fhir.r5.model.StringType;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.ExtensionContextType;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionMappingComponent;
import org.hl7.fhir.r5.model.StructureDefinition.TypeDerivationRule;
import org.hl7.fhir.r5.model.TimeType;
import org.hl7.fhir.r5.model.UnsignedIntType;
import org.hl7.fhir.r5.model.UriType;
import org.hl7.fhir.r5.model.UrlType;
import org.hl7.fhir.r5.model.UuidType;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptReferenceComponent;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetComposeComponent;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.terminologies.ValueSetUtilities;
import org.hl7.fhir.r5.utils.FHIRPathEngine;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xls.XLSXmlParser;
import org.hl7.fhir.utilities.xls.XLSXmlParser.Sheet;

public class IgSpreadsheetParser {


  private SimpleWorkerContext context;
  private Calendar genDate;
  private String base;
  private XLSXmlParser xls;
  private Map<String, MappingSpace> mappings;
  private Map<String, List<String>> metadata = new HashMap<String, List<String>>();
  private String sheetname;
  private String name;
  private Map<String, ElementDefinitionBindingComponent> bindings = new HashMap<String, ElementDefinitionBindingComponent>();
  private Sheet sheet;
  private Bundle bundle;
  private Map<String, String> valuesetsToLoad;
  private Set<String> knownValueSetIds;
  private boolean first;

  public IgSpreadsheetParser(SimpleWorkerContext context, Calendar genDate, String base, Map<String, String> valuesetsToLoad, boolean first, Map<String, MappingSpace> mappings, Set<String> knownValueSetIds) throws Exception {
    this.context = context;
    this.genDate = genDate;
    this.base = base;
    this.first = first;
    this.valuesetsToLoad = valuesetsToLoad;
    this.knownValueSetIds = knownValueSetIds;
    valuesetsToLoad.clear();
    this.mappings = mappings;
  }

  private void message(FetchedFile f, String msg, String html, IssueType type, IssueSeverity level) {
    f.getErrors().add(new ValidationMessage(Source.Publisher, type, -1, -1, f.getName(), msg, html, level));
  }

  // take the spreadsheet, and convert it to a bundle of
  // conformance resources
  public Bundle parse(FetchedFile f) throws Exception {
    try {
      name = f.getName();
      bundle = new Bundle();
      bundle.setType(BundleType.COLLECTION);
      bundle.setId(name);
      xls = new XLSXmlParser(new ByteArrayInputStream(f.getSource()), f.getName());
      checkMappings();
      loadBindings();
      loadMetadata(f);
      loadExtensions(f.getErrors());
      List<String> namedSheets = new ArrayList<String>();
      if (hasMetadata("title")) {
        f.setTitle(metadata("title"));
        if (hasMetadata("name"))
          f.setName(metadata("name"));
      } else {
        if (hasMetadata("name")) {
          f.setTitle(metadata("name"));
          f.setName(metadata("name"));
        }
      }

      StructureDefinition first = null;
      if (hasMetadata("published.structure")) {
        for (String n : metadata.get("published.structure")) {
          if (!Utilities.noString(n)) {
            supplementMappings(n);
          }
        }
        for (String n : metadata.get("published.structure")) {
          if (!Utilities.noString(n)) {
            StructureDefinition sd = parseProfileSheet(n, namedSheets, f.getErrors(), false);
            if (first == null)
              first = sd;
          }
        }

        int i = 0;
        while (i < namedSheets.size()) {
          parseProfileSheet(namedSheets.get(i), namedSheets, f.getErrors(), false);
          i++;
        }
      } else if (!hasMetadata("extensions")) {
        first = parseProfileSheet("Data Elements", namedSheets, f.getErrors(), true);
      }
      if (namedSheets.isEmpty() && xls.getSheets().containsKey("Search"))
        readSearchParams(xls.getSheets().get("Search"), first);

      if (xls.getSheets().containsKey("Operations"))
        readOperations(loadSheet("Operations"));

      if (first != null)
        processMetadata(first);

      checkOutputs(f);

    } catch (Exception e) {
      throw new Exception("exception parsing pack "+f.getName()+": "+e.getMessage(), e);
    }

    return bundle;
  }

  private void supplementMappings(String name) {
    sheet = loadSheet(name);
    if (sheet==null)
      throw new Error("Unable to load sheet " + name);
    List<String> mappingNames = sheet.getColumnNamesBySuffix(" Mapping");
    List<String> loadedMappings = new ArrayList<String>();
    for (MappingSpace m : mappings.values()) {
      loadedMappings.add(m.getColumnName());
    }
    mappingNames.removeAll(loadedMappings);
    int i = 0 - mappingNames.size();
    for (String column : mappingNames) {
      i++;
      String mapname = column.substring(0, column.length() - 8);
      MappingSpace m = new MappingSpace(column, mapname, mapname.toLowerCase().replace(' ',  '-'), i, true, false, false, null);
      mappings.put("http://unknown.org/" + mapname, m);
    }
  }
  private void processMetadata(StructureDefinition first) {
    if (hasMetadata("logical-mapping-prefix"))
      ToolingExtensions.addStringExtension(first, ToolingExtensions.EXT_MAPPING_PREFIX, metadata("logical-mapping-prefix"));
    if (hasMetadata("logical-mapping-suffix"))
      ToolingExtensions.addStringExtension(first, ToolingExtensions.EXT_MAPPING_SUFFIX, metadata("logical-mapping-suffix"));
  }

  private void checkOutputs(FetchedFile f) throws Exception {
//    File path = new File(name);
//    StringBuilder sb = new StringBuilder();
//    StringBuilder sh = new StringBuilder();
//    sb.append("Resources generated by processing "+path.getName()+":");
//    sh.append("<p>Resources generated by processing "+path.getName()+":</p></ul>");
    for (BundleEntryComponent be : bundle.getEntry()) {
      CanonicalResource b = (CanonicalResource) be.getResource();
      if (!tail(b.getUrl()).equals(b.getId()))
        throw new Exception("resource id/url mismatch: "+b.getId()+" vs "+b.getUrl());
      if (!b.getUrl().startsWith(base+"/"+b.fhirType()))
        throw new Exception("base/ resource url mismatch: "+base+" vs "+b.getUrl());
      if (!b.getUrl().equals(be.getFullUrl()))
        throw new Exception("resource url/entry url mismatch: "+b.getUrl()+" vs "+be.getFullUrl());
//      sb.append("  "+b.getUrl()+" (\""+b.getName()+"\")");
//      sh.append("<li>"+b.getUrl()+" (\""+b.getName()+"\")</li>");
    }
//    if (first) {
//      message(f, sb.toString(), sh.toString(), IssueType.INFORMATIONAL, IssueSeverity.INFORMATION);
//    }
  }


  private StructureDefinition parseProfileSheet(String n, List<String> namedSheets, List<ValidationMessage> issues, boolean logical) throws Exception {
    StructureDefinition sd = new StructureDefinition();

    Map<String, ElementDefinitionConstraintComponent> invariants = new HashMap<String, ElementDefinitionConstraintComponent>();
    String name = logical ? "Invariants" : n+"-Inv";
    sheet = loadSheet(name);
    if (sheet != null)
      invariants = readInvariants(sheet, n, name);

    sheet = loadSheet(n);
    if (sheet == null)
      throw new Exception("The StructureDefinition referred to a tab by the name of '"+n+"', but no tab by the name could be found");
    for (int row = 0; row < sheet.rows.size(); row++) {
      ElementDefinition e = processLine(sd, sheet, row, invariants, true, row == 0);
      if (e != null)
        for (TypeRefComponent t : e.getType()) {
          if (t.hasProfile() && !"Extension".equals(t.getWorkingCode()) && t.getProfile().get(0).getValue().startsWith("#")) {
            if (!namedSheets.contains(t.getProfile().get(0).getValue().substring(1)))
              namedSheets.add(t.getProfile().get(0).getValue().substring(1));
          }
        }
    }

    if (logical) {
      sd.setKind(StructureDefinitionKind.LOGICAL);
      sd.setId(sd.getDifferential().getElement().get(0).getPath());
      sd.getDifferential().getElementFirstRep().getType().clear();
      sd.setType(sd.getDifferential().getElementFirstRep().getPath());
      sd.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Element");
      sd.setDerivation(TypeDerivationRule.SPECIALIZATION);
      sd.setAbstract(false);
    } else {
      sd.setKind(StructureDefinitionKind.RESOURCE);
      sd.setDerivation(TypeDerivationRule.CONSTRAINT);
      sd.setAbstract(false);
      sd.setId(n.toLowerCase());
      sd.setType(sd.getDifferential().getElementFirstRep().getPath());
      if (sd.getDifferential().getElementFirstRep().hasSliceName()) {
        sd.setName(sd.getDifferential().getElementFirstRep().getSliceName());
        sd.getDifferential().getElementFirstRep().setSliceName(null);
      }
      if (sd.getDifferential().getElementFirstRep().getType().size() == 1 && sd.getDifferential().getElementFirstRep().getType().get(0).hasProfile())
        sd.setBaseDefinition(sd.getDifferential().getElementFirstRep().getType().get(0).getProfile().get(0).getValue());
      else
        sd.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/"+sd.getType());
      if (!context.getResourceNames().contains(sd.getType()) && !context.getTypeNames().contains(sd.getType()))
        throw new Exception("Unknown Resource "+sd.getType());
    }
    sd.getDifferential().getElementFirstRep().getType().clear();
    sd.setUrl(base+"/StructureDefinition/"+sd.getId());
    if ("http://hl7.org/fhir".equals(base))
      sd.setVersion(Constants.VERSION);
    bundle.addEntry().setResource(sd).setFullUrl(sd.getUrl());

    // Changed the default from metadata to Short because the former caused problems when there are multiple sheets in a workbook
    if (sheet.hasColumn(0, "Definition"))
      sd.setDescription(sheet.getColumn(0, "Definition"));
    if (sheet.hasColumn(0, "Profile.title"))
      sd.setTitle(sheet.getColumn(0, "Profile.title"));
    else if (sd.getDifferential().getElementFirstRep().hasShort())
      sd.setTitle(sd.getDifferential().getElementFirstRep().getShort());
    if (!sd.hasName()) {
      if (!sd.hasName() && sd.getDifferential().getElementFirstRep().hasShort())
        sd.setName(sd.getDifferential().getElementFirstRep().getShort());
      else if (hasMetadata("name"))
        sd.setName(metadata("name"));
      else if (sheet.hasColumn(0, "Profile.name"))
        sd.setName(sheet.getColumn(0, "Profile.name"));
      else
        sd.setName("UNKNOWN");
    }
    sheet = loadSheet(n + "-Extensions");
    if (sheet != null) {
      int row = 0;
      while (row < sheet.rows.size()) {
        if (sheet.getColumn(row, "Code").startsWith("!"))
          row++;
        else
          row = processExtension(sheet, row, metadata("extension.uri"), issues, invariants);
      }
    }

    sheet = loadSheet(n+"-Search");
    if (sheet != null) {
      readSearchParams(sd, sheet, true);
    }

    if (invariants != null) {
      for (ElementDefinitionConstraintComponent inv : invariants.values()) {
        if (Utilities.noString(inv.getUserString("context")))
          throw new Exception("Profile "+sd.getId()+" Invariant "+inv.getId()+" has no context");
        else {
          ElementDefinition ed = findContext(sd, inv.getUserString("context"), "Profile "+sd.getId()+" Invariant "+inv.getId()+" Context");
          ed.getConstraint().add(inv);
          if (Utilities.noString(inv.getXpath())) {
            throw new Exception("Profile "+sd.getId()+" Invariant "+inv.getId()+" ("+inv.getHuman()+") has no XPath statement");
          }
          if (Utilities.noString(inv.getExpression())) {
            throw new Exception("Profile "+sd.getId()+" Invariant "+inv.getId()+" ("+inv.getHuman()+") has no Expression statement");
          }
          else if (inv.getXpath().contains("\""))
            throw new Exception("Profile "+sd.getId()+" Invariant "+inv.getId()+" ("+inv.getHuman()+") contains a \" character: "+inv.getXpath());
        }
      }
    }

    sd.setPublisher(metadata("author.name"));
    if (Utilities.noString(metadata("experimental")))
      sd.setExperimental("true".equals(metadata("experimental")));
    if (hasMetadata("author.reference"))
      sd.addContact().getTelecom().add(Factory.newContactPoint(ContactPointSystem.URL, metadata("author.reference")));
    if (hasMetadata("date"))
      sd.setDateElement(Factory.newDateTime(metadata("date").substring(0, 10)));
    else
      sd.setDate(genDate.getTime());
      if (hasMetadata("description"))
        sd.setDescription(metadata("description"));
    if (hasMetadata("version"))
      sd.setVersion(metadata("version"));
    if (hasMetadata("status"))
      sd.setStatus(PublicationStatus.fromCode(metadata("status")));
    else
      sd.setStatus(PublicationStatus.DRAFT);

    ProfileUtilities utils = new ProfileUtilities(this.context, issues, null);
    utils.setIds(sd, false);
    return sd;
  }

  private ElementDefinition findContext(StructureDefinition sd, String context, String message) throws Exception {
    for (ElementDefinition ed : sd.getDifferential().getElement())
      if (ed.getPath().equals(context))
        return ed;
    throw new Exception("No Context found for "+context+" at "+message);
  }

  private void readSearchParams(Sheet sheet, StructureDefinition sd) throws Exception {
    if (sheet != null) {
      for (int row = 0; row < sheet.rows.size(); row++) {
        if (!sheet.hasColumn(row, "Name"))
          throw new Exception("Search Param has no name "+ getLocation(row));
        String n = sheet.getColumn(row, "Name");
        if (!n.startsWith("!")) {
          SearchParameter sp = new SearchParameter();
          sp.setId(sd.getId()+"-"+n);
          sp.setName("Search Parameter "+n);
          sp.setUrl(base+"/SearchParameter/"+sp.getId());
          sp.setStatus(sd.getStatus());
          sp.setExperimental(sd.getExperimental());
          if (!sheet.hasColumn(row, "Type"))
            throw new Exception("Search Param "+sd.getId()+"-"+n+" has no type "+ getLocation(row));
          sp.setType(readSearchType(sheet.getColumn(row, "Type"), row));
          sp.setDescription(sheet.getColumn(row, "Description"));
          sp.setXpathUsage(readSearchXPathUsage(sheet.getColumn(row, "Expression Usage"), row));
          sp.setXpath(sheet.getColumn(row, "XPath"));
          sp.setExpression(sheet.getColumn(row, "Expression"));
          if (!sp.hasExpression())
            sp.setExpression(sheet.getColumn(row, "Path"));
          if (!sp.hasDescription())
            throw new Exception("Search Param "+sd.getId()+"/"+n+" has no description "+ getLocation(row));
//          FHIRPathEngine engine = new FHIRPathEngine(context);
//          engine.check(null, sd.getType(), sd.getType(), sp.getExpression());
//          bundle.addEntry().setResource(sp).setFullUrl(sp.getUrl());
        }
      }
    }
  }


  private void loadExtensions(List<ValidationMessage> issues) throws Exception {
    Map<String,ElementDefinitionConstraintComponent> invariants = null;
    sheet = loadSheet("Extensions-Inv");
    if (sheet != null) {
      invariants = readInvariants(sheet, "", "Extensions-Inv");
    }
    sheet = loadSheet("Extensions");
    if (sheet != null) {
      int row = 0;
      while (row < sheet.rows.size()) {
        if (sheet.getColumn(row, "Code").startsWith("!"))
          row++;
        else
          row = processExtension(sheet, row, metadata("extension.uri"), issues, invariants);
      }
    }
  }

  private void loadBindings() throws Exception {
    sheet = loadSheet("Bindings");
    if (sheet != null)
      readBindings(sheet);
  }

  private void loadMetadata(FetchedFile f) throws Exception {
    sheet = loadSheet("Metadata");
    if (sheet != null) {
      for (int row = 0; row < sheet.rows.size(); row++) {
        String n = sheet.getColumn(row, "Name");
        String v = sheet.getColumn(row, "Value");
        if (n != null && v != null) {
          if (metadata.containsKey(n))
            metadata.get(n).add(v);
          else {
            ArrayList<String> vl = new ArrayList<String>();
            vl.add(v);
            metadata.put(n, vl);
          }
        }
      }
    }
    if (!hasMetadata("extension.uri") || !metadata("extension.uri").startsWith(base))
      message(f, "extension.uri must be defined for IG spreadsheets, and must start with "+base, null, IssueType.BUSINESSRULE, IssueSeverity.ERROR);
  }

  private Sheet loadSheet(String name) {
    sheetname = name;
    return xls.getSheets().get(name);
  }


  private void checkMappings() throws FHIRException {
    Sheet sheet = loadSheet("Mappings");
    if (sheet != null) {
      for (int row = 0; row < sheet.rows.size(); row++) {
        String uri = sheet.getNonEmptyColumn(row, "Uri");
        MappingSpace ms = new MappingSpace(sheet.getNonEmptyColumn(row, "Column"), sheet.getNonEmptyColumn(row, "Title"), sheet.getNonEmptyColumn(row, "Id"), sheet.getIntColumn(row, "Sort Order"), true, false, false, sheet.hasColumn(row, "Link") ?  sheet.getColumn(row, "Link") : uri);
        mappings.put(uri, ms);
      }
    }
  }

  private void readBindings(Sheet sheet) throws Exception {
    for (int row = 0; row < sheet.rows.size(); row++) {
      String bindingName = sheet.getColumn(row, "Binding Name");

      // Ignore bindings whose name start with "!"
      if (Utilities.noString(bindingName) || bindingName.startsWith("!")) continue;

      ElementDefinitionBindingComponent bs = new ElementDefinitionBindingComponent();
      bindings.put(bindingName, bs);
      bs.setDescription(sheet.getColumn(row, "Definition"));
      bs.setStrength(readBindingStrength(sheet.getColumn(row, "Conformance"), row));

      if (sheet.hasColumn("max-valueset") && !Utilities.noString(sheet.getColumn(row, "max-valueset"))) {
        bs.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/elementdefinition-maxValueSet").setValue(new CanonicalType(sheet.getColumn(row, "max-valueset")));
      }

      if (sheet.hasColumn("min-valueset") && !Utilities.noString(sheet.getColumn(row, "min-valueset"))) {
                bs.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/elementdefinition-minValueSet").setValue(new CanonicalType(sheet.getColumn(row, "min-valueset")));
      }

      String type = sheet.getColumn(row, "Binding");
      if (type == null || "".equals(type) || "unbound".equals(type)) {
        // nothing
      } else if (type.equals("code list")) {
//        throw new Error("Code list is not yet supported in "+ getLocation(row));
        String ref = sheet.getColumn(row, "Reference");

        ValueSet vs = ValueSetUtilities.makeShareable(new ValueSet());
        vs.setId(ref.substring(1));
        vs.setUrl(base+"/ValueSet/"+ref.substring(1));
        if (sheet.hasColumn("Version"))
          vs.setVersion(sheet.getColumn(row, "Version"));
        else
          vs.setVersion(metadata("version"));
        if (Utilities.noString(metadata("experimental")))
          vs.setExperimental("true".equals(metadata("experimental")));
        vs.setPublisher(metadata("author.name"));
        bs.setValueSet(vs.getUrl()+"|");
        bundle.addEntry().setResource(vs).setFullUrl(vs.getUrl());
        vs.setName(bindingName);
        String oid = sheet.getColumn(row, "Oid");
        if (!Utilities.noString(oid))
          ValueSetUtilities.setOID(vs, oid);
        String st = sheet.getColumn(row, "Status");
        if (Utilities.noString(st))
          st = "draft";
        vs.getStatusElement().setValueAsString(st);
        String ws = sheet.getColumn(row, "Website");
        if (ws != null)
          vs.getContactFirstRep().getTelecomFirstRep().setSystem(ContactPointSystem.URL).setValue(ws);
        String em = sheet.getColumn(row, "Website");
        if (em != null)
          vs.getContactFirstRep().addTelecom().setSystem(ContactPointSystem.EMAIL).setValue(em);
        vs.setCopyright(sheet.getColumn(row, "Copyright"));
        vs.setDescription(sheet.getColumn(row, "Definition"));
        Sheet css = xls.getSheets().get(ref.substring(1));
        if (css == null)
          throw new Exception("Error parsing binding "+bindingName+": code list reference '"+ref+"' not resolved");
        loadValueSet(vs, css, ref.substring(1));
      } else if (type.equals("special")) {
        throw new Error("Binding type Special is not allowed in implementation guides"+ getLocation(row));
      } else if (type.equals("reference")) {
        throw new Exception("The binding type 'reference' is no longer supported");
      } else if (type.equals("value set")) {
        String ref = sheet.getColumn(row, "Reference");
        String id = ref.startsWith("valueset-") ? ref.substring(9) : ref;
        if (!ref.startsWith("http:") && !ref.startsWith("https:") && !ref.startsWith("ValueSet/")) {
          valuesetsToLoad.put(id, ref);
          ref = Utilities.pathURL(base, "ValueSet", id);
        }
        bs.setValueSet(ref);
      } else {
        throw new Exception("Unknown Binding: "+type+ getLocation(row));
      }
    }
  }

  private void loadValueSet(ValueSet vs, Sheet sheet, String sheetName) throws Exception {
    boolean hasDefine = false;
    for (int row = 0; row < sheet.rows.size(); row++) {
      hasDefine = hasDefine || Utilities.noString(sheet.getColumn(row, "System"));
    }

    Map<String, ConceptDefinitionComponent> codes = new HashMap<String, ConceptDefinitionComponent>();
    Map<String, ConceptDefinitionComponent> codesById = new HashMap<String, ConceptDefinitionComponent>();

    Map<String, ConceptSetComponent> includes = new HashMap<String, ConceptSetComponent>();


    if (hasDefine) {
      CodeSystem cs = new CodeSystem();
      cs.setUrl(base+"/CodeSystem/"+sheetName);
      vs.getCompose().addInclude().setSystem(cs.getUrl());
      CodeSystemConvertor.populate(cs, vs);
      cs.setVersion(vs.getVersion());
      cs.setCaseSensitive(true);
      cs.setContent(CodeSystemContentMode.COMPLETE);
      bundle.addEntry().setResource(cs).setFullUrl(cs.getUrl());

      for (int row = 0; row < sheet.rows.size(); row++) {
        if (Utilities.noString(sheet.getColumn(row, "System"))) {

          ConceptDefinitionComponent cc = new ConceptDefinitionComponent();
          cc.setUserData("id", sheet.getColumn(row, "Id"));
          cc.setCode(sheet.getColumn(row, "Code"));
          if (codes.containsKey(cc.getCode()))
            throw new Exception("Duplicate Code '"+cc.getCode()+"' processing "+vs.getName());
          codes.put(cc.getCode(), cc);
          codesById.put(cc.getUserString("id"), cc);
          cc.setDisplay(sheet.getColumn(row, "Display"));
          if (sheet.getColumn(row, "Abstract").toUpperCase().equals("Y"))
            CodeSystemUtilities.setNotSelectable(cs, cc);
          if (cc.hasCode() && !cc.hasDisplay())
            cc.setDisplay(Utilities.humanize(cc.getCode()));
          cc.setDefinition(sheet.getColumn(row, "Definition"));
          if (!Utilities.noString(sheet.getColumn(row, "Comment")))
            ToolingExtensions.addCSComment(cc, sheet.getColumn(row, "Comment"));
//          cc.setUserData("v2", sheet.getColumn(row, "v2"));
//          cc.setUserData("v3", sheet.getColumn(row, "v3"));
          for (String ct : sheet.columns)
            if (ct.startsWith("Display:") && !Utilities.noString(sheet.getColumn(row, ct)))
              cc.addDesignation().setLanguage(ct.substring(8)).setValue(sheet.getColumn(row, ct));
          String parent = sheet.getColumn(row, "Parent");
          if (Utilities.noString(parent))
            cs.addConcept(cc);
          else if (parent.startsWith("#") && codesById.containsKey(parent.substring(1)))
            codesById.get(parent.substring(1)).addConcept(cc);
          else if (codes.containsKey(parent))
            codes.get(parent).addConcept(cc);
          else
            throw new Exception("Parent "+parent+" not resolved in "+sheetName);
        }
      }
    }

    for (int row = 0; row < sheet.rows.size(); row++) {
      if (!Utilities.noString(sheet.getColumn(row, "System"))) {
        String system = sheet.getColumn(row, "System");
        ConceptSetComponent t = includes.get(system);
        if (t == null) {
          if (!vs.hasCompose())
            vs.setCompose(new ValueSetComposeComponent());
          t = vs.getCompose().addInclude();
          t.setSystem(system);
          includes.put(system, t);
        }
        ConceptReferenceComponent cc = t.addConcept();
        cc.setCode(sheet.getColumn(row, "Code"));
        if (codes.containsKey(cc.getCode()))
          throw new Exception("Duplicate Code "+cc.getCode());
        codes.put(cc.getCode(), null);
        cc.setDisplay(sheet.getColumn(row, "Display"));
        if (!Utilities.noString(sheet.getColumn(row, "Definition")))
          ToolingExtensions.addDefinition(cc, sheet.getColumn(row, "Definition"));
        if (!Utilities.noString(sheet.getColumn(row, "Comment")))
          ToolingExtensions.addVSComment(cc, sheet.getColumn(row, "Comment"));
        cc.setUserDataINN("v2", sheet.getColumn(row, "v2"));
        cc.setUserDataINN("v3", sheet.getColumn(row, "v3"));
        for (String ct : sheet.columns)
          if (ct.startsWith("Display:") && !Utilities.noString(sheet.getColumn(row, ct)))
            cc.addDesignation().setLanguage(ct.substring(8)).setValue(sheet.getColumn(row, ct));
      }
    }

  }

  public BindingStrength readBindingStrength(String s, int row) throws Exception {
    s = s.toLowerCase();
    if (s.equals("required") || s.equals(""))
      return BindingStrength.REQUIRED;
    if (s.equals("extensible"))
      return BindingStrength.EXTENSIBLE;
    if (s.equals("preferred"))
      return BindingStrength.PREFERRED;
    if (s.equals("example"))
      return BindingStrength.EXAMPLE;
    throw new Exception("Unknown Binding Strength: '"+s+"'"+ getLocation(row));
  }


  private Map<String,ElementDefinitionConstraintComponent> readInvariants(Sheet sheet, String id, String sheetName) throws Exception {

    Map<String,ElementDefinitionConstraintComponent> result = new HashMap<String,ElementDefinitionConstraintComponent>();
    for (int row = 0; row < sheet.rows.size(); row++) {
      ElementDefinitionConstraintComponent inv = new ElementDefinitionConstraintComponent();

      String s = sheet.getColumn(row, "Id");
      if (!s.startsWith("!")) {
        inv.setKey(s);
        inv.setRequirements(sheet.getColumn(row, "Requirements"));
        String sev = sheet.getColumn(row, "Severity");
        if ("bp".equals(sev)) {
          inv.setSeverity(ConstraintSeverity.WARNING);
          inv.addExtension().setUrl("http://hl7.org/fhir/StructureDefinition/elementdefinition-bestpractice").setValue(new BooleanType(true));
        } else
          inv.getSeverityElement().setValueAsString(sev);
        inv.setHuman(sheet.getColumn(row, "English"));
        inv.setExpression(sheet.getColumn(row, "Expression"));
        inv.setXpath(sheet.getColumn(row, "XPath"));
        if (s.equals("") || result.containsKey(s))
          throw new Exception("duplicate or missing invariant id "+ getLocation(row));
        inv.setUserData("context", sheet.getColumn(row, "Context"));
        result.put(s, inv);
      }
    }

    return result;
  }

  private String getLocation(int row) {
    return name + ", sheet \""+sheetname+"\", row " + Integer.toString(row + 2);
  }

  private ElementDefinition processLine(StructureDefinition sd, Sheet sheet, int row, Map<String, ElementDefinitionConstraintComponent> invariants, boolean profile, boolean firstTime) throws Exception {
    String path = sheet.getColumn(row, "Element");

    if (path.startsWith("!"))
      return null;
    if (Utilities.noString(path))
      throw new Exception("Error reading definitions - no path found @ " + getLocation(row));

    if (path.contains("#"))
      throw new Exception("Old path style @ " + getLocation(row));

    String profileName = sheet.getColumn(row, "Profile Name");
    String discriminator = sheet.getColumn(row, "Discriminator");

    boolean isRoot = !path.contains(".");

    ElementDefinition e = sd.getDifferential().addElement();
    if (isRoot) {
      e.setPath(path);
    } else {
      String arc = path.substring(0, path.lastIndexOf("."));
      String leaf = path.substring(path.lastIndexOf(".")+1);
      if (leaf.startsWith("@")) {
        leaf = leaf.substring(1);
        e.addRepresentation(PropertyRepresentation.XMLATTR);
      }
      e.setPath(arc+"."+leaf);
    }
    String c = sheet.getColumn(row, "Card.");
    if (c == null || c.equals("") || c.startsWith("!")) {
    } else {
      String[] card = c.split("\\.\\.");
      if (card.length != 2 || !Utilities.isInteger(card[0]) || (!"*".equals(card[1]) && !Utilities.isInteger(card[1])))
        throw new Exception("Unable to parse Cardinality '" + c + "' " + c + " in " + getLocation(row) + " on " + path);
      e.setMin(Integer.parseInt(card[0]));
      e.setMax("*".equals(card[1]) ? "*" : card[1]);
    }
    e.setSliceName(profileName);
    if (!Utilities.noString(discriminator)) {
      e.getSlicing().setRules(SlicingRules.OPEN);
      if (discriminator.contains("|")) {
        throw new Error("We don't yet support ordered or non-open slicing when defining profiles with spreadsheets: " + discriminator);
      }
      for (String d : discriminator.split("\\,"))
        if (!Utilities.noString(d))
          e.getSlicing().addDiscriminator(ProfileUtilities.interpretR2Discriminator(d.trim(), false));
      if (e.hasSliceName()) {
        throw new Error("Slice name '"+profileName+"' on a root which has slicing: "+e.getPath());
      }
    }
    doAliases(sheet, row, e);

    if (sheet.hasColumn(row, "Is Modifier"))
      e.setIsModifier(parseBoolean(sheet.getColumn(row, "Is Modifier"), row, false));
    if (e.getIsModifier()) {
      String reason = sheet.getColumn(row, "Modifier Reason");
      if (Utilities.noString(reason)) {
        if (path.endsWith(".modifierExtension")) {
          reason = "Modifier extensions are expected to modify the meaning or interpretation of the element that contains them";
        } else {
          System.out.println("Missing IsModifierReason on "+path);
          reason = "Not known why this is labelled a modifier";
        }
      }
      e.setIsModifierReason(reason);
    } else if (path.endsWith(".modifierExtension")) {
      e.setIsModifier(true);
      e.setIsModifierReason("Modifier extensions are expected to modify the meaning or interpretation of the element that contains them");
    }
    // later, this will get hooked in from the underlying definitions, but we need to know this now to validate the extension modifier matching
    if (e.getPath().endsWith(".modifierExtension"))
      e.setIsModifier(true);
    e.setMustSupport(parseBoolean(sheet.getColumn(row, "Must Support"), row, false));

    if (sheet.hasColumn(row, "Summary"))
      e.setIsSummary(parseBoolean(sheet.getColumn(row, "Summary"), row, false));

    String uml = sheet.getColumn(row, "UML");
    if (!Utilities.noString(uml)) {
      if (uml.contains(";")) {
        String[] parts = uml.split("\\;");
        e.setUserData("SvgLeft", parts[0]);
        e.setUserData("SvgTop", parts[1]);
        if (parts.length > 2)
          e.setUserData("SvgWidth", parts[2]);
      } else if (uml.startsWith("break:")) {
        e.setUserData("UmlBreak", true);
        e.setUserData("UmlDir", uml.substring(6));
      } else {
        e.setUserData("UmlDir", uml);
      }
    }
    String s = sheet.getColumn(row, "Condition");
    if (s != null && !s.equals(""))
      throw new Exception("Found Condition in spreadsheet "+ getLocation(row));
    s = sheet.getColumn(row, "Inv.");
    if (s != null && !s.equals("")) {
      for (String sn : s.split(",")) {
        ElementDefinitionConstraintComponent inv = invariants.get(sn);
        if (inv == null)
          throw new Exception("unable to find Invariant '" + sn + "' "   + getLocation(row));
        e.addCondition(inv.getKey());
      }
    }

    if (!Utilities.noString(sheet.getColumn(row, "Type"))) {
      TypeParser tp = new TypeParser();
      List<TypeRef> types = tp.parse(sheet.getColumn(row, "Type"), true, metadata("extension.uri"), context, !path.contains("."));
      if (types.size() == 1 && types.get(0).getName().startsWith("@"))
        e.setContentReference("#"+types.get(0).getName().substring(1));
      else if (types.size() > 0)
        e.getType().addAll(tp.convert(context, e.getPath(), types, true, e));
    }
    String regex = sheet.getColumn(row, "Regex");
    if (!Utilities.noString(regex) && e.hasType())
      ToolingExtensions.addStringExtension(e.getType().get(0), ToolingExtensions.EXT_REGEX, regex);

    if ((path.endsWith(".extension") || path.endsWith(".modifierExtension")) && e.hasType() && e.getType().get(0).hasProfile() && Utilities.noString(profileName))
        throw new Exception("need to have a profile name if a profiled extension is referenced for "+ e.getType().get(0).getProfile());

    String bindingName = sheet.getColumn(row, "Binding");
    if (!Utilities.noString(bindingName)) {
      ElementDefinitionBindingComponent binding = bindings.get(bindingName);
      if (binding == null && !bindingName.startsWith("!"))
        throw new Exception("Binding name "+bindingName+" could not be resolved in local spreadsheet");
      e.setBinding(binding);
    }
    e.setShort(sheet.getColumn(row, "Short Name"));


    e.setDefinition(Utilities.appendPeriod(processDefinition(sheet.getColumn(row, "Definition"))));

    if (!Utilities.noString(sheet.getColumn(row, "Max Length")))
      e.setMaxLength(Integer.parseInt(sheet.getColumn(row, "Max Length")));
    e.setRequirements(Utilities.appendPeriod(sheet.getColumn(row, "Requirements")));
    if (e.hasRequirements() && !e.getPath().contains(".")) {
      sd.setPurpose(e.getRequirements());
      e.setRequirements(null);
    }
    e.setComment(Utilities.appendPeriod(sheet.getColumn(row, "Comments")));
    for (String n : mappings.keySet()) {
      MappingSpace m = mappings.get(n);
      String sm = sheet.getColumn(row, mappings.get(n).getColumnName());
      if (!Utilities.noString(sm)) {
        ElementDefinitionMappingComponent map = e.addMapping();
        map.setIdentity(m.getId());
        map.setMap(sm);
        boolean found = false;
        for (StructureDefinitionMappingComponent mm : sd.getMapping()) {
          if (mm.getIdentity().equals(m.getId()))
            found = true;
        }
        if (!found) {
          StructureDefinitionMappingComponent mm = sd.addMapping();
          mm.setIdentity(m.getId());
          mm.setName(m.getTitle());
          mm.setUri(n);
        }
      }
    }
    if (!Utilities.noString(sheet.getColumn(row, "Example")))
      e.addExample().setLabel("General").setValue(processValue(sheet, row, "Example", sheet.getColumn(row, "Example"), e));
    processOtherExamples(e, sheet, row);
    String dh = sheet.getColumn(row, "Display Hint");
    if (!Utilities.noString(dh))
      ToolingExtensions.addDisplayHint(e, dh);
    e.setFixed(processValue(sheet, row, "Value", sheet.getColumn(row, "Value"), e));
    e.setPattern(processValue(sheet, row, "Pattern", sheet.getColumn(row, "Pattern"), e));
    return e;
  }

  private void processOtherExamples(ElementDefinition e, Sheet sheet, int row) throws Exception {
    for (int i = 1; i <= 20; i++) {
      String s = sheet.getColumn(row, "Example "+Integer.toString(i));
      if (Utilities.noString(s))
        s = sheet.getByColumnPrefix(row, "Example "+Integer.toString(i)+" (");
      if (s.contains("//"))
        s = s.substring(0,  s.indexOf("//")).trim();
      if (!Utilities.noString(s)) {
        DataType v = processStringToType(e.getTypeFirstRep().getWorkingCode(), s, e.getPath());
        if (v != null) {
          Extension ex = e.addExtension();
          ex.setUrl("http://hl7.org/fhir/StructureDefinition/structuredefinition-example");
          ex.addExtension().setUrl("index").setValue(new StringType(Integer.toString(i)));
          ex.addExtension().setUrl("exValue").setValue(v);
        }
      }
    }
  }

  private DataType processStringToType(String type, String s, String path) throws Exception {
    s = s.trim();
    if (s.equalsIgnoreCase("Not Stated") || s.equalsIgnoreCase("n/a") || s.equalsIgnoreCase("-"))
      return null;
    if (Utilities.noString(type))
      return new StringType(s);
    if (type.equals("Reference")) {
      return new Reference().setReference(s);
    }
    if (type.equals("Quantity")) {
      int j = s.charAt(0) == '>' || s.charAt(0) == '<' ? 1 : 0;
      int i = j;
      while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.'))
        i++;
      if (i == j)
        throw new Exception("Error parsing quantity value '"+s+"': must have the format [d][u] e.g. 50mm on "+path);
      Quantity q = new Quantity();
      q.setValue(new BigDecimal(s.substring(j, i)));
      if (i < s.length()) {
        q.setUnit(s.substring(i).trim());
        q.setCode(s.substring(i).trim());
        q.setSystem("http://unitsofmeasure.org");
      }
      if (j > 0)
        q.setComparator(QuantityComparator.fromCode(s.substring(0, j)));
      return q;
    }
    return new StringType(s);
  }

  private String processDefinition(String definition) {
    // preProcessMarkdown(...
    return definition.replace("$version$", Constants.VERSION);
  }

  private void doAliases(Sheet sheet, int row, ElementDefinition e) throws Exception {
    String aliases = sheet.getColumn(row, "Aliases");
    if (!Utilities.noString(aliases))
      if (aliases.contains(";")) {
        for (String a : aliases.split(";"))
          e.addAlias(a.trim());
      } else {
        for (String a : aliases.split(","))
          e.addAlias(a.trim());
      }
  }

  protected Boolean parseBoolean(String s, int row, Boolean def) throws Exception {
    if (s == null || s.isEmpty())
      return def;
    s = s.toLowerCase();
    if (s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes")
        || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("1"))
      return true;
    else if (s.equals("false") || s.equals("0") || s.equals("f")
        || s.equals("n") || s.equals("no"))
      return false;
    else
      throw new Exception("unable to process boolean value: " + s
          + " in " + getLocation(row));
  }

  private DataType processValue(Sheet sheet, int row, String column, String source, ElementDefinition e) throws Exception {
    if (Utilities.noString(source))
      return null;
    if (e.getType().size() != 1)
      throw new Exception("Unable to process "+column+" unless a single type is specified @ "+getLocation(row)+", type = \""+e.typeSummary()+"\", column = "+column);
    String type = e.getType().get(0).getWorkingCode();
    StructureDefinition sd = context.fetchTypeDefinition(type);
    if (sd != null && sd.hasBaseDefinition() && sd.getDerivation() == TypeDerivationRule.CONSTRAINT)
      type = sd.getType();

    if (source.startsWith("{")) {
      JsonParser json = new JsonParser();
      try {
        return json.parseType(source, type);
      } catch (Exception e2) {
        throw new Exception("Unable to parse json string: " + source+" as "+type+" because "+e2.getMessage(), e2);
      }
    } else if (source.startsWith("<")) {
      XmlParser xml = new XmlParser();
      try {
        return xml.parseType(source, type);
      } catch (Exception e2) {
        throw new Exception("Unable to parse xml string: " + source+" as "+type+" because "+e2.getMessage(), e2);
      }
    } else {
      source = source.trim();
      if (source.startsWith("\"") && source.endsWith("\""))
        source = source.substring(1, source.length()-1);

      if (type.equals("string"))
        return new StringType(source);
      if (type.equals("boolean"))
        return new BooleanType(Boolean.valueOf(source));
      if (type.equals("integer"))
        return new IntegerType(Integer.valueOf(source));
      if (type.equals("unsignedInt"))
        return new UnsignedIntType(Integer.valueOf(source));
      if (type.equals("positiveInt"))
        return new PositiveIntType(Integer.valueOf(source));
      if (type.equals("decimal"))
        return new DecimalType(new BigDecimal(source));
      if (type.equals("base64Binary"))
        return new Base64BinaryType(source);
      if (type.equals("instant"))
        return new InstantType(source);
      if (type.equals("uri"))
        return new UriType(source);
      if (type.equals("url"))
        return new UrlType(source);
      if (type.equals("canonical"))
        return new CanonicalType(source);
      if (type.equals("date"))
        return new DateType(source);
      if (type.equals("dateTime"))
        return new DateTimeType(source);
      if (type.equals("time"))
        return new TimeType(source);
      if (type.equals("code"))
        return new CodeType(source);
      if (type.equals("oid"))
        return new OidType(source);
      if (type.equals("uuid"))
        return new UuidType(source);
      if (type.equals("id"))
        return new IdType(source);
      if (type.startsWith("Reference(")) {
        Reference r = new Reference();
        r.setReference(source);
        return r;
      }
      if (type.equals("Period")) {
        if (source.contains("->")) {
          String[] parts = source.split("\\-\\>");
          Period p = new Period();
          p.setStartElement(new DateTimeType(parts[0].trim()));
          if (parts.length > 1)
            p.setEndElement(new DateTimeType(parts[1].trim()));
          return p;

        } else
          throw new Exception("format not understood parsing "+source+" into a period");
      }
      if (type.equals("CodeableConcept")) {
        CodeableConcept cc = new CodeableConcept();
        if (source.contains(":")) {
          String[] parts = source.split("\\:");
          String system = "";
          if (parts[0].equalsIgnoreCase("SCT"))
            system = "http://snomed.info/sct";
          else if (parts[0].equalsIgnoreCase("LOINC"))
            system = "http://loinc.org";
          else if (parts[0].equalsIgnoreCase("AMTv2"))
            system = "http://nehta.gov.au/amtv2";
          else
            system = "http://hl7.org/fhir/"+parts[0];
          String code = parts[1];
          String display = parts.length > 2 ? parts[2] : null;
          cc.addCoding().setSystem(system).setCode(code).setDisplay(display);
        } else
          throw new Exception("format not understood parsing "+source+" into a codeable concept");
        return cc;
      }
      if (type.equals("Identifier")) {
        Identifier id = new Identifier();
        id.setSystem("urn:ietf:rfc:3986");
        id.setValue(source);
        return id;
      }
      if (type.equals("Quantity")) {
        int s = 0;
        if (source.startsWith("<=") || source.startsWith("=>"))
          s = 2;
        else if (source.startsWith("<") || source.startsWith(">"))
          s = 1;
        int i = s;
        while (i < source.length() && Character.isDigit(source.charAt(i)))
          i++;
        Quantity q = new Quantity();
        if (s > 0)
          q.setComparator(QuantityComparator.fromCode(source.substring(0, s)));
        if (i > s)
          q.setValue(new BigDecimal(source.substring(s, i)));
        if (i < source.length())
          q.setUnit(source.substring(i).trim());
        return q;
      }

      throw new Exception("Unable to process primitive value '"+source+"' provided for "+column+" - unhandled type "+type+" @ " +getLocation(row));
    }
  }

  private String tail(String url) {
    return url.substring(url.lastIndexOf("/")+1);
  }

  private int processExtension(Sheet sheet, int row,  String uri, List<ValidationMessage> issues, Map<String, ElementDefinitionConstraintComponent> invariants) throws Exception {
    // first, we build the extension definition
    String name = sheet.getColumn(row, "Code");
    StructureDefinition ex = new StructureDefinition();
    ex.setUrl(uri+name);
    ex.setId(tail(ex.getUrl()));
    bundle.addEntry().setResource(ex).setFullUrl(ex.getUrl());
    ex.setKind(StructureDefinitionKind.COMPLEXTYPE);
    ex.setType("Extension");
    ex.setBaseDefinition("http://hl7.org/fhir/StructureDefinition/Extension");
    ex.setDerivation(TypeDerivationRule.CONSTRAINT);
    ex.setAbstract(false);
    if ("http://hl7.org/fhir".equals(base))
      ex.setVersion(Constants.VERSION);
    else
      ex.setVersion(sheet.getColumn(row, "Version"));

    String context = null;
    if (Utilities.noString(name))
      throw new Exception("No code found on Extension at "+getLocation(row));

    if (name.contains("."))
      throw new Exception("Extension Definition Error: Extension names cannot contain '.': "+name+"  at "+getLocation(row));

    // ap.getExtensions().add(ex);

    if (context == null) {
      ExtensionContextType ct = readContextType(sheet.getColumn(row, "Context Type"), row);
      if (sheet.hasColumn("Context Invariant"))
        for (String s : sheet.getColumn(row, "Context Invariant").split("~"))
          ex.addContextInvariant(s);
      String cc = sheet.getColumn(row, "Context");
      if (!Utilities.noString(cc))
        for (String c : cc.split("\\;")) {
          StructureDefinitionContextComponent ec = ex.addContext();
          ec.setType(ct);
          ec.setExpression(c.trim());
          checkContextValid(ec, this.name);
        }
    }
    ex.setTitle(sheet.getColumn(row, "Display"));
    ElementDefinition exe = ex.getDifferential().addElement();
    exe.setPath("Extension");
//    exe.setSliceName(sheet.getColumn(row, "Code"));

    ElementDefinition exu = ex.getDifferential().addElement();
    exu.setPath("Extension.url");
    exu.setFixed(new UriType(ex.getUrl()));
    TypeRefComponent tc = new TypeRefComponent("uri");
    List<TypeRefComponent> tcList = new ArrayList<TypeRefComponent>();
    tcList.add(tc);
    exu.setType(tcList);

    if (invariants != null) {
      for (ElementDefinitionConstraintComponent inv : invariants.values()) {
        if (inv.getKey().equals(name))
          exe.getConstraint().add(inv);
      }
    }

    parseExtensionElement(sheet, row, ex, exe, false);
    String sl = exe.getShort();
    ex.setName(sheet.getColumn(row, "Name"));
    if (!ex.hasName())
      ex.setName(name);
    if (!Utilities.noString(sl) && !sl.contains("|") && !ex.hasTitle())
      ex.setName(sl);
//    ex.setName("Extension "+ex.getId()+(ex.hasDisplay() ? " "+ex.getDisplay() : ""));
    if (sheet.hasColumn(0, "Profile.name"))
      ex.setName(sheet.getColumn(0, "Profile.name"));
    if (sheet.hasColumn(0, "Profile.title"))
      ex.setTitle(sheet.getColumn(0, "Profile.title"));

    if (!ex.hasName())
      throw new Exception("Extension "+ex.getUrl()+" missing name at "+getLocation(row));
    ex.setDescription(exe.getDefinition());

    ex.setPublisher(metadata("author.name"));
    if (Utilities.noString(metadata("experimental")))
      ex.setExperimental("true".equals(metadata("experimental")));
    if (hasMetadata("author.reference"))
      ex.addContact().getTelecom().add(Factory.newContactPoint(ContactPointSystem.URL, metadata("author.reference")));
    //  <code> opt Zero+ Coding assist with indexing and finding</code>
    if (hasMetadata("date"))
      ex.setDateElement(Factory.newDateTime(metadata("date").substring(0, 10)));
    else
      ex.setDate(genDate.getTime());

    if (hasMetadata("status"))
      ex.setStatus(PublicationStatus.fromCode(metadata("status")));

    row++;
    boolean hasChild = false;
    while (row < sheet.getRows().size() && sheet.getColumn(row, "Code").startsWith(name+".")) {
      hasChild = true;
      String n = sheet.getColumn(row, "Code");
      ElementDefinition child = ex.getDifferential().addElement();
      child.setPath("Extension.extension");
      child.setSliceName(n.substring(n.lastIndexOf(".")+1));
      exu = ex.getDifferential().addElement();
      exu.setPath("Extension.extension.url");
      exu.setFixed(new UriType(child.getSliceName()));
      parseExtensionElement(sheet, row, ex, child, true);
      if (invariants != null) {
        for (ElementDefinitionConstraintComponent inv : invariants.values()) {
          if (inv.getKey().equals(n))
            child.getConstraint().add(inv);
        }
      }
      row++;
    }
    if (hasChild) {
      boolean found = false;
      for (ElementDefinition exv : ex.getDifferential().getElement())
        if (exv.getPath().startsWith("Extension.value")) {
          found = true;
          exv.setMax("0");
        }
      if (!found) {
        ex.getDifferential().addElement().setPath("Extension.value[x]").setMax("0");
      }
    }
    ex.getDifferential().getElementFirstRep().getType().clear();
    if (ex.getDifferential().getElementFirstRep().hasRequirements()) {
      ex.setPurpose(ex.getDifferential().getElementFirstRep().getRequirements());
      ex.getDifferential().getElementFirstRep().setRequirements(null);
    }
    if (ex.getDifferential().getElementFirstRep().hasLabel()) {
      ex.setTitle(ex.getDifferential().getElementFirstRep().getLabel());
      ex.getDifferential().getElementFirstRep().setLabel(null);
    }
    if (ex.getDifferential().getElementFirstRep().hasCode()) {
      ex.getKeyword().addAll(ex.getDifferential().getElementFirstRep().getCode());
      ex.getDifferential().getElementFirstRep().getCode().clear();
    }

    StructureDefinition base = this.context.fetchResource(StructureDefinition.class, "http://hl7.org/fhir/StructureDefinition/Extension");
    List<String> errors = new ArrayList<String>();
    ProfileUtilities utils = new ProfileUtilities(this.context, issues, null);
    utils.sortDifferential(base, ex, "extension "+ex.getUrl(), errors, false);
    assert(errors.size() == 0);
    utils.setIds(ex, false);
    return row;
  }

  private boolean hasMetadata(String name) {
    return metadata.containsKey(name) && metadata.get(name).size() > 0 && !Utilities.noString(metadata.get(name).get(0));
  }

  private String metadata(String name) {
    if (!metadata.containsKey(name))
      return "";
    List<String> a = metadata.get(name);
    if (a.size() == 1)
      return a.get(0);
    else
      return "";
  }

  private ExtensionContextType readContextType(String value, int row) throws Exception {
    if (value.equals("Resource"))
      return ExtensionContextType.ELEMENT;
    if (value.equals("DataType") || value.equals("Data Type"))
      return ExtensionContextType.ELEMENT;
    if (value.equals("Elements"))
      return ExtensionContextType.ELEMENT;
    if (value.equals("Element"))
      return ExtensionContextType.ELEMENT;
    if (value.equals("Extension"))
      return ExtensionContextType.ELEMENT;
    throw new Exception("Unable to read context type '"+value+"' at "+getLocation(row));
  }

  public void checkContextValid(StructureDefinitionContextComponent ec, String context) throws Exception {
    if (ec.getType() == ExtensionContextType.ELEMENT) {
      if (ec.getExpression().equals("*"))
        ec.setExpression("Element");
      if (ec.getExpression().equals("Any"))
        ec.setExpression("Resource");

      String[] parts = ec.getExpression().split("\\.");
      StructureDefinition sd = this.context.fetchTypeDefinition(parts[0]);
      if (sd != null) {
        for (ElementDefinition ed : sd.getSnapshot().getElement())
          if (ed.getPath().equals(ec.getExpression()))
            return;
      }
      throw new Error("The element context '"+ec.getExpression()+"' is not valid @ "+context);
    } else if (ec.getType() == ExtensionContextType.EXTENSION) {
      if (!Utilities.isAbsoluteUrl(ec.getExpression()))
        throw new Error("The extension context '" + ec.getExpression() + "' is not valid @ "+context);
    } else
      throw new Error("The extension context '" + ec.getExpression() + "' is not supported yet @ "+context);
  }

  private void parseExtensionElement(Sheet sheet, int row, StructureDefinition sd, ElementDefinition exe, boolean nested) throws Exception {
    // things that go on Extension
    String[] card = sheet.getColumn(row, "Card.").split("\\.\\.");
    if (card.length != 2 || !Utilities.isInteger(card[0])
        || (!"*".equals(card[1]) && !Utilities.isInteger(card[1])))
      throw new Exception("Unable to parse Cardinality "
          + sheet.getColumn(row, "Card.") + " in " + getLocation(row));
    exe.setMin(Integer.parseInt(card[0]));
    exe.setMax("*".equals(card[1]) ? "*" : card[1]);
    String s = sheet.getColumn(row, "Condition");
    if (!Utilities.noString(s))
      exe.addCondition(s);
    exe.setDefinition(Utilities.appendPeriod(processDefinition(sheet.getColumn(row, "Definition"))));
    exe.setRequirements(Utilities.appendPeriod(sheet.getColumn(row, "Requirements")));
    exe.setComment(Utilities.appendPeriod(sheet.getColumn(row, "Comments")));
    doAliases(sheet, row, exe);
    for (String n : mappings.keySet()) {
      MappingSpace m = mappings.get(n);
      String sm = sheet.getColumn(row, mappings.get(n).getColumnName());
      if (!Utilities.noString(sm)) {
        ElementDefinitionMappingComponent map = exe.addMapping();
        map.setIdentity(m.getId());
        map.setMap(sm);
      }
    }
    exe.setShort(sheet.getColumn(row, "Short Name"));

    if (sheet.hasColumn(row, "Is Modifier"))
      exe.setIsModifier(parseBoolean(sheet.getColumn(row, "Is Modifier"), row, false));
    if (exe.getIsModifier()) {
      String reason = sheet.getColumn(row, "Modifier Reason");
      if (Utilities.noString(reason)) {
        System.out.println("Missing IsModifierReason on "+getLocation(row));
        reason = "Not known why this is labelled a modifier";
      }
      exe.setIsModifierReason(reason);
    }
    if (nested && exe.getIsModifier())
      throw new Exception("Cannot create a nested extension that is a modifier @"+getLocation(row));
    exe.getType().add(new TypeRefComponent().setCode("Extension"));

    // things that go on Extension.value
    if (!Utilities.noString(sheet.getColumn(row, "Type"))) {
      ElementDefinition exv = new ElementDefinition();
      TypeParser tp = new TypeParser();
      List<TypeRef> types = tp.parse(sheet.getColumn(row, "Type"), true, metadata("extension.uri"), context, false);
      exv.getType().addAll(tp.convert(context, exv.getPath(), types, false, exv));
      if (exv.getType().size()>1) {
//        exv.setPath(exe.getPath()+".valueReference");
        exv.setPath(exe.getPath()+".value[x]");
        for (TypeRefComponent t : exv.getType()) {
          if (!t.getWorkingCode().equals("Reference")) {
            exv.setPath(exe.getPath()+".value[x]");
            break;
          }
        }
      } else {
        TypeRefComponent type = exv.getType().get(0);
/*        if (type.getCode().equals("*") || type.get.getParams().size()>1)
          exv.setName("value[x]");
        else {*/
          String name = type.getWorkingCode();
          exv.setPath(exe.getPath()+".value[x]");
//          exv.setPath(exe.getPath()+".value" + name.substring(0,1).toUpperCase() + name.substring(1));
//        }
      }

      sd.getDifferential().getElement().add(exv);
      String bindingName = sheet.getColumn(row, "Binding");
      if (!Utilities.noString(bindingName)) {
        ElementDefinitionBindingComponent binding = bindings.get(bindingName);
        if (binding == null && !bindingName.startsWith("!"))
          throw new Exception("Binding name "+bindingName+" could not be resolved in local spreadsheet");
        exv.setBinding(binding);
      }
      // exv.setBinding();
      s = sheet.getColumn(row, "Max Length");
      if (!Utilities.noString(s))
        exv.setMaxLength(Integer.parseInt(s));
      if (!Utilities.noString(sheet.getColumn(row, "Example")))
        exv.addExample().setLabel("General").setValue(processValue(sheet, row, "Example", sheet.getColumn(row, "Example"), exv));
    }
  }

  private void readSearchParams(StructureDefinition sd, Sheet sheet, boolean forProfile) throws Exception {
    if (sheet != null) {
      for (int row = 0; row < sheet.rows.size(); row++) {

        if (!sheet.hasColumn(row, "Name"))
          throw new Exception("Search Param has no name "+ getLocation(row));
        String n = sheet.getColumn(row, "Name");
        if (!n.startsWith("!")) {
          if (n.endsWith("-before") || n.endsWith("-after"))
            throw new Exception("Search Param "+sd.getName()+"/"+n+" includes relative time "+ getLocation(row));
          SearchParameter sp = new SearchParameter();
          sp.setId(sd.getId()+"-"+n);
          sp.setName("Search Parameter "+n);
          sp.setUrl(base+"/SearchParameter/"+sp.getId());
          sp.setStatus(sd.getStatus());
          sp.setExperimental(sd.getExperimental());

          if (!sheet.hasColumn(row, "Type"))
            throw new Exception("Search Param "+sd.getName()+"/"+n+" has no type "+ getLocation(row));
          sp.setType(readSearchType(sheet.getColumn(row, "Type"), row));
          sp.setDescription(sheet.getColumn(row, "Description"));
          sp.setXpathUsage(readSearchXPathUsage(sheet.getColumn(row, "Expression Usage"), row));
          sp.setXpath(sheet.getColumn(row, "XPath"));
          sp.setExpression(sheet.getColumn(row, "Expression"));
          if (!sp.hasDescription())
            throw new Exception("Search Param "+sd.getId()+"/"+n+" has no description "+ getLocation(row));
          if (!sp.hasXpathUsage())
            throw new Exception("Search Param "+sd.getId()+"/"+n+" has no expression usage "+ getLocation(row));
          FHIRPathEngine engine = new FHIRPathEngine(context);
          engine.check(null, sd.getType(), sd.getType(), sp.getExpression());
          bundle.addEntry().setResource(sp).setFullUrl(sp.getUrl());
        }
      }
    }
  }

  private SearchParamType readSearchType(String s, int row) throws Exception {
    if ("number".equals(s))
      return SearchParamType.NUMBER;
    if ("string".equals(s))
      return SearchParamType.STRING;
    if ("date".equals(s))
      return SearchParamType.DATE;
    if ("reference".equals(s))
      return SearchParamType.REFERENCE;
    if ("token".equals(s))
      return SearchParamType.TOKEN;
    if ("uri".equals(s))
      return SearchParamType.URI;
    if ("composite".equals(s))
      return SearchParamType.COMPOSITE;
    if ("quantity".equals(s))
      return SearchParamType.QUANTITY;
    if ("special".equals(s))
      return SearchParamType.QUANTITY;
    throw new Exception("Unknown Search Type '" + s + "': " + getLocation(row));
  }

  private SearchParameter.XPathUsageType readSearchXPathUsage(String s, int row) throws Exception {
    if (Utilities.noString(s))
      return SearchParameter.XPathUsageType.NORMAL;
    if ("normal".equals(s))
      return SearchParameter.XPathUsageType.NORMAL;
    if ("nearby".equals(s))
      return SearchParameter.XPathUsageType.NEARBY;
    if ("distance".equals(s))
      return SearchParameter.XPathUsageType.DISTANCE;
    if ("phonetic".equals(s))
      return SearchParameter.XPathUsageType.PHONETIC;
    throw new Exception("Unknown Search Path Usage '" + s + "' at " + getLocation(row));
  }

  private void readOperations(Sheet sheet) throws Exception {
    Map<String, OperationDefinition> ops = new HashMap<String, OperationDefinition>();
    Map<String, OperationDefinitionParameterComponent> params = new HashMap<String, OperationDefinitionParameterComponent>();

    if (sheet != null) {
      for (int row = 0; row < sheet.rows.size(); row++) {
        String name = sheet.getColumn(row, "Name");
        String use = sheet.getColumn(row, "Use");
        String doco = sheet.getColumn(row, "Documentation");
        String type = sheet.getColumn(row, "Type");

        if (name != null && !name.equals("") && !name.startsWith("!")) {
          if (!name.contains(".")) {
            if (!type.equals("operation"))
              throw new Exception("Invalid type on operation "+type+" at " +getLocation(row));
            if (!name.toLowerCase().equals(name))
              throw new Exception("Invalid name on operation "+name+" - must be all lower case (use dashes) at " +getLocation(row));

            params.clear();

            boolean system = false;
            boolean istype = false;
            boolean instance = false;
            for (String c : use.split("\\|")) {
              c = c.trim();
              if ("system".equalsIgnoreCase(c))
                system = true;
              else if ("resource".equalsIgnoreCase(c))
                istype = true;
              else if ("instance".equalsIgnoreCase(c))
                instance = true;
              else
                throw new Exception("unknown operation use code "+c+" at "+getLocation(row));
            }
            OperationDefinition op = new OperationDefinition();
            op.setId(name);
            op.setUrl(base+"/OperationDefinition/"+name);
            op.setSystem(system);
            op.setInstance(istype);
            op.setVersion(Constants.VERSION);
            String s = sheet.getColumn(row, "Type");
            if (!Utilities.noString(s)) {
              op.addResource(s);
              op.setType(true);
            }
            s = sheet.getColumn(row, "Title");
            if (!Utilities.noString(s))
              op.setName(s);
            bundle.addEntry().setResource(op).setFullUrl(op.getUrl());
            ops.put(name, op);
          } else {
            String context = name.substring(0, name.lastIndexOf('.'));
            String pname = name.substring(name.lastIndexOf('.')+1);
            OperationDefinition operation;
            List<OperationDefinitionParameterComponent> plist;
            if (context.contains(".")) {
              String opname = name.substring(0, name.indexOf('.'));
              // inside of a tuple
              if (!Utilities.noString(use))
                throw new Exception("Tuple parameters: use must be blank at "+getLocation(row));
              operation = ops.get(opname);
              if (operation == null)
                throw new Exception("Unknown Operation '"+opname+"' at "+getLocation(row));
              OperationDefinitionParameterComponent param = params.get(context);
              if (param == null)
                throw new Exception("Tuple parameter '"+context+"' not found at "+getLocation(row));
              if (!param.getType().equals("Tuple"))
                throw new Exception("Tuple parameter '"+context+"' type must be Tuple at "+getLocation(row));
              plist = param.getPart();
            } else {
              if (!use.equals("in") && !use.equals("out"))
                throw new Exception("Only allowed use is 'in' or 'out' at "+getLocation(row));
              operation = ops.get(context);
              if (operation == null)
                throw new Exception("Unknown Operation '"+context+"' at "+getLocation(row));
              plist = operation.getParameter();
            }
            String profile = sheet.getColumn(row, "Profile");
            String min = sheet.getColumn(row, "Min");
            String max = sheet.getColumn(row, "Max");
            OperationDefinitionParameterComponent p = new OperationDefinitionParameterComponent();
            p.setName(pname);
            p.getUseElement().setValueAsString(use);
            p.setDocumentation(doco);
            p.setMin(Integer.parseInt(min));
            p.setMax(max);
            p.setType(Enumerations.FHIRAllTypes.fromCode(type));
            p.getSearchTypeElement().setValueAsString(sheet.getColumn(row, "Search Type"));
            p.addTargetProfile(profile);
            String bs = sheet.getColumn(row, "Binding");
            if (!Utilities.noString(bs)) {
              ElementDefinitionBindingComponent b = bindings.get(bs);
              if (b == null)
                throw new Exception("Unable to find binding "+bs);
              p.getBinding().setStrength(b.getStrength());
              p.getBinding().setValueSet(b.getValueSet());
            }
            plist.add(p);
            params.put(name, p);
          }
        }
      }
    }
  }

}
