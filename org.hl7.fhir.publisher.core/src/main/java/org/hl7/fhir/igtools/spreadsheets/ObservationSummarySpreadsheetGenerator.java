package org.hl7.fhir.igtools.spreadsheets;

import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.hl7.fhir.igtools.renderers.CrossViewRenderer.ObservationProfile;
import org.hl7.fhir.igtools.renderers.CrossViewRenderer.UsedType;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingComponent;
import org.hl7.fhir.r5.renderers.spreadsheets.SpreadsheetGenerator;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;

public class ObservationSummarySpreadsheetGenerator extends SpreadsheetGenerator {


  public ObservationSummarySpreadsheetGenerator(IWorkerContext context) {
    super(context);
  }

  public ObservationSummarySpreadsheetGenerator generate(List<ObservationProfile> list) {
    Sheet sheet = makeSheet("Observations");

    Row headerRow = sheet.createRow(0);
    addCell(headerRow, 0, "Profile", styles.get("body"));
    addCell(headerRow, 1, "Name");
    addCell(headerRow, 2, "Category Code");
    addCell(headerRow, 3, "Category VS");
    addCell(headerRow, 4, "Code");
    addCell(headerRow, 5, "Code VS");
    addCell(headerRow, 6, "Time Types");
    addCell(headerRow, 7, "Value Types");
    addCell(headerRow, 8, "Data Absent Reason");
    addCell(headerRow, 9, "Body Site");
    addCell(headerRow, 10, "Method");

    for (ObservationProfile op : list) {
      Row row = sheet.createRow(sheet.getLastRowNum()+1);
      addCell(row, 0, op.source.getId());
      addCell(row, 1, op.source.present());
      addCell(row, 2, renderCodes(op.category));
      addCell(row, 3, renderVS(op.catVS));
      addCell(row, 4, renderCodes(op.code));
      addCell(row, 5, renderVS(op.codeVS));
      addCell(row, 6, renderTypes(op.effectiveTypes));
      addCell(row, 7, renderTypes(op.types));
      addCell(row, 8, renderBoolean(op.dataAbsentReason));
      addCell(row, 9, renderCodes(op.bodySite));
      addCell(row, 10, renderCodes(op.method));
      
      for (ObservationProfile op2 : op.components) {
        row = sheet.createRow(sheet.getLastRowNum()+1);
        addCell(row, 0, "");
        addCell(row, 1, op.source.present());
        addCell(row, 2, "");
        addCell(row, 3, "");
        addCell(row, 4, renderCodes(op2.code));
        addCell(row, 5, renderVS(op2.codeVS));
        addCell(row, 6, renderTypes(op2.effectiveTypes));
        addCell(row, 7, renderTypes(op2.types));
        addCell(row, 8, renderBoolean(op2.dataAbsentReason));
        addCell(row, 9, renderCodes(op2.bodySite));
        addCell(row, 10, renderCodes(op2.method));
      }
    }

    return this;
  }

  private String renderCodes(List<Coding> codes) {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(", ");
    for (Coding t : codes) {
      String sys = t.getUserString(UserDataNames.xver_desc);
      b.append(sys+"#"+t.getCode());
    }
    return b.toString();
  }

  private String renderTypes(List<UsedType> types) {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder(", ");
    for (UsedType t : types) {
      if (t.ms) {
        b.append(t.name+(char) 0x0135);
      } else {
        b.append(t.name);
      }
    }
    return b.toString();
  }

  private String renderVS(ElementDefinitionBindingComponent binding) {
    if (binding == null) {
      return "";
    } else {
      return binding.getValueSet()+" ("+binding.getStrength().toCode()+")";
    }
  }

  private String renderBoolean(Boolean bool) {
    if (bool == null) {
      return "optional";
    } else if (bool) {
      return "required";
    } else {
      return "prohibited";
    }
  }
}
