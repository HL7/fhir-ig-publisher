package org.hl7.fhir.igtools.spreadsheets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ColumnWidthCacheTest {

  private static final String[] TEXTS = {"", "Y", "0..1", "Reference(Patient | Practitioner)", "a\nbb\nccc",
      "Ünïcödé ✓", "  padded  ", "a long definition that keeps going for quite a while to make a wide column, wider than most"};

  /** a sheet with string cells in four styles (plain, bold, indented, rotated), numbers, booleans and gaps */
  private static XSSFWorkbook workbook(long seed, boolean merged) {
    Random rnd = new Random(seed);
    XSSFWorkbook wb = new XSSFWorkbook();
    CellStyle plain = wb.createCellStyle();
    CellStyle bold = wb.createCellStyle();
    Font bf = wb.createFont();
    bf.setBold(true);
    bold.setFont(bf);
    CellStyle indented = wb.createCellStyle();
    indented.setIndention((short) 2);
    CellStyle rotated = wb.createCellStyle();
    rotated.setRotation((short) 45);
    Font big = wb.createFont();
    big.setFontHeightInPoints((short) 16);
    big.setItalic(true);
    big.setUnderline(Font.U_SINGLE);
    CellStyle bigStyle = wb.createCellStyle();
    bigStyle.setFont(big);
    CellStyle[] styles = {plain, bold, indented, rotated, bigStyle, style(wb, f -> f.setFontHeightInPoints((short) 16)),
        style(wb, f -> f.setItalic(true)), style(wb, f -> f.setUnderline(Font.U_SINGLE)), style(wb, f -> f.setFontName("Monospaced"))};
    Sheet s = wb.createSheet("S");
    for (int r = 0; r < 60; r++) {
      if (r % 13 == 7) {
        continue; // a missing row
      }
      Row row = s.createRow(r);
      for (int c = 0; c < 6; c++) {
        if (rnd.nextInt(8) == 0) {
          continue; // a missing cell
        }
        switch (c) {
          case 4: row.createCell(c).setCellValue(rnd.nextInt(100000) / 7.0); break;
          case 5: row.createCell(c).setCellValue(rnd.nextBoolean()); break;
          default:
            row.createCell(c).setCellValue(TEXTS[rnd.nextInt(TEXTS.length)] + (rnd.nextInt(3) == 0 ? "" + r : ""));
            row.getCell(c).setCellStyle(styles[rnd.nextInt(styles.length)]);
        }
      }
    }
    if (merged) {
      s.addMergedRegion(new CellRangeAddress(2, 2, 0, 1));
    }
    return wb;
  }

  /** a style whose font differs from the default in one way */
  private static CellStyle style(XSSFWorkbook wb, Consumer<Font> change) {
    Font f = wb.createFont();
    change.accept(f);
    CellStyle style = wb.createCellStyle();
    style.setFont(f);
    return style;
  }

  private static void assertSameWidths(boolean merged, BiConsumer<Sheet, Integer> sizer) throws Exception {
    for (long seed = 0; seed < 5; seed++) {
      try (XSSFWorkbook a = workbook(seed, merged); XSSFWorkbook b = workbook(seed, merged)) {
        XSSFSheet sa = a.getSheetAt(0);
        XSSFSheet sb = b.getSheetAt(0);
        for (int c = 0; c < 7; c++) {
          sa.autoSizeColumn(c);
          sizer.accept(sb, c);
          assertEquals(sa.getColumnWidth(c), sb.getColumnWidth(c), "seed " + seed + " column " + c);
          assertEquals(sa.getColumnHelper().getColumn(c, false) == null, sb.getColumnHelper().getColumn(c, false) == null);
          if (sa.getColumnHelper().getColumn(c, false) != null) {
            assertEquals(sa.getColumnHelper().getColumn(c, false).toString(), sb.getColumnHelper().getColumn(c, false).toString());
          }
        }
      }
    }
  }

  @Test
  void sameWidthsAsPoi() throws Exception {
    ColumnWidthCache cache = new ColumnWidthCache();
    assertSameWidths(false, cache::autoSizeColumn);
    assertTrue(cache.size() > 0);
    // and again with every text already cached
    int size = cache.size();
    assertSameWidths(false, cache::autoSizeColumn);
    assertEquals(size, cache.size());
  }

  @Test
  void sameWidthsAsPoiWithMergedRegions() throws Exception {
    ColumnWidthCache cache = new ColumnWidthCache();
    assertSameWidths(true, cache::autoSizeColumn);
    assertEquals(0, cache.size(), "sheets with merged regions are left to POI");
  }

  @Test
  void sameWidthsWithArbitraryExtraWidth() throws Exception {
    ColumnWidthCache cache = new ColumnWidthCache();
    try (XSSFWorkbook a = workbook(1, false); XSSFWorkbook b = workbook(1, false)) {
      a.getSheetAt(0).setArbitraryExtraWidth(300);
      b.getSheetAt(0).setArbitraryExtraWidth(300);
      for (int c = 0; c < 7; c++) {
        a.getSheetAt(0).autoSizeColumn(c);
        cache.autoSizeColumn(b.getSheetAt(0), c);
        assertEquals(a.getSheetAt(0).getColumnWidth(c), b.getSheetAt(0).getColumnWidth(c));
      }
    }
  }

  @Test
  void sameWidthsAcrossWorkbooksWithOtherDefaultFonts() throws Exception {
    // the same text in the same font, in workbooks whose default font (and so character width) differs
    ColumnWidthCache cache = new ColumnWidthCache();
    for (short defaultPoints : new short[] {11, 20, 8}) {
      try (XSSFWorkbook a = new XSSFWorkbook(); XSSFWorkbook b = new XSSFWorkbook()) {
        for (XSSFWorkbook wb : new XSSFWorkbook[] {a, b}) {
          wb.getFontAt(0).setFontHeightInPoints(defaultPoints);
          CellStyle style = style(wb, f -> f.setFontHeightInPoints((short) 11));
          Sheet s = wb.createSheet("S");
          for (int r = 0; r < TEXTS.length; r++) {
            s.createRow(r).createCell(0).setCellValue(TEXTS[r]);
            s.getRow(r).getCell(0).setCellStyle(style);
          }
        }
        a.getSheetAt(0).autoSizeColumn(0);
        cache.autoSizeColumn(b.getSheetAt(0), 0);
        assertEquals(a.getSheetAt(0).getColumnWidth(0), b.getSheetAt(0).getColumnWidth(0), "default font " + defaultPoints + "pt");
      }
    }
  }
}
