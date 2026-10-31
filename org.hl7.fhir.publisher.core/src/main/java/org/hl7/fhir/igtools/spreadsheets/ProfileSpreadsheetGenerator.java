package org.hl7.fhir.igtools.spreadsheets;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FontFormatting;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.hl7.fhir.model.core.CodeableConcept;
import org.hl7.fhir.model.core.Coding;
import org.hl7.fhir.model.core.ContactDetail;
import org.hl7.fhir.model.core.DataType;
import org.hl7.fhir.model.core.Identifier;
import org.hl7.fhir.model.core.StructureDefinition;
import org.hl7.fhir.model.core.StructureDefinition.StructureDefinitionMappingComponent;
import org.hl7.fhir.services.context.IWorkerContext;
import org.hl7.fhir.services.renderers.DataRenderer;
import org.hl7.fhir.services.renderers.RendererFactory;
import org.hl7.fhir.services.renderers.spreadsheets.StructureDefinitionSpreadsheetGenerator;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTAutoFilter;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCustomFilter;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCustomFilters;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTFilterColumn;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTFilters;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STFilterOperator;

/**
 * The core {@link StructureDefinitionSpreadsheetGenerator}, producing the same workbook, with three additions:
 * <ul>
 *   <li>the workbook's shared strings go through a {@link CachingSharedStringsTable};</li>
 *   <li>column auto-sizing goes through a {@link ColumnWidthCache}, shared by all the profile workbooks of a build;</li>
 *   <li>{@link #precomputeDisplays(StructureDefinition)} works out, up front, the few values the generator
 *   renders through the worker context (identifiers, contacts, jurisdictions, keywords), so that
 *   {@link #renderStructureDefinition} can then run on another thread without touching the context.</li>
 * </ul>
 * {@link #configureSheet(Sheet, StructureDefinition)} is a copy of the core method (core 7.0.2) with only the
 * {@code autoSizeColumn} calls changed, and the styles are made by a copy of core's. ProfileSpreadsheetGeneratorTest checks the two give the same workbook.
 */
public class ProfileSpreadsheetGenerator extends StructureDefinitionSpreadsheetGenerator {

  /** StructureDefinitionSpreadsheetGenerator.titles.length (private in core); checked by the test */
  static final int TITLE_COUNT = 36;

  private final boolean hideMustSupportFalse;
  private final ColumnWidthCache widths;

  public ProfileSpreadsheetGenerator(IWorkerContext context, boolean valuesAsXml, boolean hideMustSupportFalse,
                                     RendererFactory renderer, ColumnWidthCache widths) {
    super(context, valuesAsXml, hideMustSupportFalse, renderer);
    this.hideMustSupportFalse = hideMustSupportFalse;
    this.widths = widths;
    // the same workbook and styles as core makes, but with a faster shared strings table
    wb = CachingSharedStringsTable.newWorkbook();
    styles = createStyles(wb);
  }

  /** a copy of SpreadsheetGenerator.createStyles (private in core); the test checks the workbooks are the same */
  private static Map<String, CellStyle> createStyles(Workbook wb) {
    Map<String, CellStyle> styles = new HashMap<>();

    CellStyle style;
    Font headerFont = wb.createFont();
    headerFont.setBold(true);
    style = createBorderedStyle(wb);
    style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setVerticalAlignment(VerticalAlignment.TOP);
    style.setWrapText(true);
    style.setFont(headerFont);
    styles.put("header", style);

    style = createBorderedStyle(wb);
    style.setVerticalAlignment(VerticalAlignment.TOP);
    style.setWrapText(true);
    styles.put("body", style);

    return styles;
  }

  private static CellStyle createBorderedStyle(Workbook wb) {
    BorderStyle thin = BorderStyle.THIN;
    short black = IndexedColors.GREY_50_PERCENT.getIndex();

    CellStyle style = wb.createCellStyle();
    style.setBorderRight(thin);
    style.setRightBorderColor(black);
    style.setBorderBottom(thin);
    style.setBottomBorderColor(black);
    style.setBorderLeft(thin);
    style.setLeftBorderColor(black);
    style.setBorderTop(thin);
    style.setTopBorderColor(black);
    return style;
  }

  /**
   * Renders now, on this thread, every value of sd that the generator would otherwise render through the worker
   * context, and makes the generator use those answers. After this, rendering sd (the same object) needs
   * nothing from the context. Asking for any other value fails with {@link IllegalStateException} instead of
   * reaching the context.
   */
  public void precomputeDisplays(StructureDefinition sd) {
    Map<DataType, String> displays = new IdentityHashMap<>();
    for (Coding k : sd.getKeywordList()) {
      displays.put(k, dr.displayDataType(k));
    }
    for (Identifier id : sd.getIdentifierList()) {
      displays.put(id, dr.displayDataType(id));
    }
    for (ContactDetail c : sd.getContactList()) {
      displays.put(c, dr.displayDataType(c));
    }
    for (CodeableConcept j : sd.getJurisdictionList()) {
      displays.put(j, dr.displayDataType(j));
    }
    dr = new PrecomputedDisplays(dr, displays);
  }

  private static class PrecomputedDisplays extends DataRenderer {
    private final Map<DataType, String> displays;

    PrecomputedDisplays(DataRenderer renderer, Map<DataType, String> displays) {
      super(renderer.getContext());
      this.displays = displays;
    }

    @Override
    public String displayDataType(DataType type) {
      if (!displays.containsKey(type)) {
        throw new IllegalStateException("No precomputed display for " + (type == null ? "null" : type.fhirType()));
      }
      return displays.get(type);
    }
  }

  @Override
  public void configureSheet(Sheet sheet, StructureDefinition sd) throws IOException {
    for (int i=0; i<34; i++) {
      widths.autoSizeColumn(sheet, i);
    }
    sheet.setColumnHidden(2, true);
    sheet.setColumnHidden(3, true);
    sheet.setColumnHidden(30, true);
    sheet.setColumnHidden(31, true);
    sheet.setColumnHidden(32, true);

    sheet.setColumnWidth(9, columnPixels(20));
    sheet.setColumnWidth(11, columnPixels(100));
    sheet.setColumnWidth(12, columnPixels(100));
    sheet.setColumnWidth(13, columnPixels(100));
    sheet.setColumnWidth(15, columnPixels(20));
    sheet.setColumnWidth(16, columnPixels(20));
    sheet.setColumnWidth(17, columnPixels(20));
    sheet.setColumnWidth(18, columnPixels(20));
    sheet.setColumnWidth(34, columnPixels(100));

    if (sd != null) {
      int i = TITLE_COUNT - 1;
      for (StructureDefinitionMappingComponent map : sd.getMappingList()) {
        i++;
        sheet.setColumnWidth(i, columnPixels(50));
        widths.autoSizeColumn(sheet, i);
      }
    }
    sheet.createFreezePane(2,1);

    if (hideMustSupportFalse) {
      SheetConditionalFormatting sheetCF = sheet.getSheetConditionalFormatting();
      String address = "A2:AI" + Math.max(Integer.valueOf(sheet.getLastRowNum()), 2);
      CellRangeAddress[] regions = {
          CellRangeAddress.valueOf(address)
      };

      ConditionalFormattingRule rule1 = sheetCF.createConditionalFormattingRule("$G2<>\"Y\"");
      PatternFormatting fill1 = rule1.createPatternFormatting();
      fill1.setFillBackgroundColor(IndexedColors.GREY_25_PERCENT.index);
      fill1.setFillPattern(PatternFormatting.SOLID_FOREGROUND);

      ConditionalFormattingRule rule2 = sheetCF.createConditionalFormattingRule("$Q2<>\"\"");
      FontFormatting font = rule2.createFontFormatting();
      font.setFontColorIndex(IndexedColors.GREY_25_PERCENT.index);
      font.setFontStyle(true, false);

      sheetCF.addConditionalFormatting(regions, rule1, rule2);

      sheet.setAutoFilter(new CellRangeAddress(0,sheet.getLastRowNum(), 0, TITLE_COUNT+(sd == null ? 0 : sd.getMappingList().size() - 1)));

      XSSFSheet xSheet = (XSSFSheet)sheet;

      final int mustSupportCol = 7;
      final int slicingCol = 27;
      CTAutoFilter sheetFilter = xSheet.getCTWorksheet().getAutoFilter();
      CTFilterColumn filterColumn1 = sheetFilter.addNewFilterColumn();
      filterColumn1.setColId(mustSupportCol); // mustSupport
      CTCustomFilters filters = filterColumn1.addNewCustomFilters();
      CTCustomFilter filter1 = filters.addNewCustomFilter();
      filter1.setOperator(STFilterOperator.NOT_EQUAL);
      filter1.setVal(" ");

      CTFilterColumn filterColumn2 = sheetFilter.addNewFilterColumn();
      filterColumn2.setColId(slicingCol); //slicing
      CTFilters filters2 = filterColumn2.addNewFilters();
      filters2.setBlank(true);

      // We have to apply the filter ourselves by hiding the rows:
      for (Row row : sheet) {
        if (row.getRowNum()>0 && (!row.getCell(mustSupportCol).getStringCellValue().equals("Y") || !row.getCell(slicingCol).getStringCellValue().isEmpty())) {
          ((XSSFRow) row).getCTRow().setHidden(true);
        }
      }
    }
    if (sheet.getLastRowNum() > 0) {
      sheet.setActiveCell(new CellAddress(sheet.getRow(1).getCell(0)));
    }
  }
}
