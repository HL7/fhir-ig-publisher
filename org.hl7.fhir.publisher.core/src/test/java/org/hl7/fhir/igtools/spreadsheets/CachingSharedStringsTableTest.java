package org.hl7.fhir.igtools.spreadsheets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Random;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRst;

class CachingSharedStringsTableTest {

  private static final String[] TEXTS = {
      "", "Y", "0..1", "Bold & <xml> \"quotes\" 'apos'", "Ünïcödé ✓ 𝄞", "line one\nline two", "a\r\nb",
      "  leading", "trailing  ", " ", "\ttab", "]]> and <![CDATA[ x ]]>", "_x0041_ escaped", "ctl \u0001 char",
      "&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&& many entities <<<<<<<<<<",
  };

  /** strings of every kind POI accepts, in a seeded order; the same seed gives the same calls */
  private static byte[] build(XSSFWorkbook wb, long seed) throws Exception {
    Random rnd = new Random(seed);
    XSSFFont bold = wb.createFont();
    bold.setBold(true);
    XSSFRichTextString shared = new XSSFRichTextString("the same instance, used again");
    Sheet s = wb.createSheet("S");
    for (int r = 0; r < 300; r++) {
      Row row = s.createRow(r);
      for (int c = 0; c < 8; c++) {
        String text = TEXTS[rnd.nextInt(TEXTS.length)] + (rnd.nextInt(4) == 0 ? " " + rnd.nextInt(20) : "");
        switch (rnd.nextInt(8)) {
          case 0: { // rich text, with a run
            XSSFRichTextString rt = new XSSFRichTextString(text);
            rt.applyFont(bold);
            row.createCell(c).setCellValue(rt);
            break;
          }
          case 1: { // a <t> without the xml:space attribute POI would give it
            CTRst st = CTRst.Factory.newInstance();
            st.setT(text);
            row.createCell(c).setCellValue(new XSSFRichTextString(st));
            break;
          }
          case 2: // a string already in the table (so not a fragment of its own)
            if (r > 0 && s.getRow(r - 1).getCell(c) != null && s.getRow(r - 1).getCell(c).getCellType() == CellType.STRING) {
              row.createCell(c).setCellValue(s.getRow(r - 1).getCell(c).getRichStringCellValue());
            }
            break;
          case 3:
            row.createCell(c).setCellValue(shared);
            break;
          case 4:
            row.createCell(c).setCellValue(rnd.nextInt(100));
            break;
          default:
            row.createCell(c).setCellValue(text);
        }
      }
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    wb.write(bytes);
    return bytes.toByteArray();
  }

  @Test
  void sameWorkbookAsPoi() throws Exception {
    for (long seed = 0; seed < 5; seed++) {
      try (XSSFWorkbook poi = new XSSFWorkbook(); XSSFWorkbook ours = CachingSharedStringsTable.newWorkbook()) {
        byte[] expected = build(poi, seed);
        byte[] actual = build(ours, seed);
        ProfileSpreadsheetGeneratorTest.assertSameWorkbook(expected, actual);
        SharedStringsTable a = poi.getSharedStringSource();
        assertTrue(ours.getSharedStringSource() instanceof CachingSharedStringsTable);
        CachingSharedStringsTable b = (CachingSharedStringsTable) ours.getSharedStringSource();
        assertEquals(a.getCount(), b.getCount());
        assertEquals(a.getUniqueCount(), b.getUniqueCount());
        assertTrue(b.size() > 0 && b.size() <= b.getUniqueCount(), "plain strings are known: " + b.size());
        assertTrue(b.getCount() > 2 * b.getUniqueCount(), "strings are used more than once");
      }
    }
  }

  @Test
  void sameXmlSpaceHandlingAsPoi() throws Exception {
    // the same text with and without xml:space are two entries for POI, so they must be here too
    try (XSSFWorkbook poi = new XSSFWorkbook(); XSSFWorkbook ours = CachingSharedStringsTable.newWorkbook()) {
      for (XSSFWorkbook wb : new XSSFWorkbook[] {poi, ours}) {
        Row row = wb.createSheet("S").createRow(0);
        CTRst bare = CTRst.Factory.newInstance();
        bare.setT(" x ");
        row.createCell(0).setCellValue(" x ");
        row.createCell(1).setCellValue(new XSSFRichTextString(bare));
        row.createCell(2).setCellValue(" x ");
        row.createCell(3).setCellValue(new XSSFRichTextString((CTRst) bare.copy()));
      }
      assertEquals(2, poi.getSharedStringSource().getUniqueCount());
      assertEquals(2, ours.getSharedStringSource().getUniqueCount());
      for (int c = 0; c < 4; c++) {
        assertEquals(poi.getSheetAt(0).getRow(0).getCell(c).getRawValue(), ours.getSheetAt(0).getRow(0).getCell(c).getRawValue(), "cell " + c);
      }
    }
  }
}
