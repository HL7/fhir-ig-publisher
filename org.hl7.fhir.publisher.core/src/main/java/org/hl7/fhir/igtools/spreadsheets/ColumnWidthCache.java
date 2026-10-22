package org.hl7.fhir.igtools.spreadsheets;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.SheetUtil;
import org.apache.poi.xssf.usermodel.XSSFSheet;

/**
 * A faster {@link Sheet#autoSizeColumn(int)} that sets exactly the same column widths.
 * <p>
 * POI measures every cell of the column with a new java.awt {@code TextLayout}, which is most of the cost of
 * a profile spreadsheet. The profile spreadsheets of an IG repeat a lot of text (element definitions, types,
 * cardinalities, empty cells), and the all-profiles workbook repeats all of it again. So this asks POI's own
 * {@link SheetUtil#getCellWidth(Cell, float, DataFormatter, boolean, List)} once for each distinct text and
 * reuses the answer.
 * <p>
 * For a string cell that answer depends only on the text, the cell font's name, size in points, bold, italic
 * and underline, the style's rotation and indent, and the workbook's default character width. All of those
 * are in the key. Everything else goes straight to POI: other cell types, sheets with merged regions, and
 * sheets that are not XSSF.
 * <p>
 * Thread-safe: one instance may be shared by threads that each work on their own workbook.
 */
public class ColumnWidthCache {

  private record Key(String fontName, short fontPoints, boolean bold, boolean italic, byte underline,
                     short rotation, short indention, int defaultCharWidthBits, String text) {
  }

  private final ConcurrentHashMap<Key, Double> widths = new ConcurrentHashMap<>();

  /**
   * Does what {@link XSSFSheet#autoSizeColumn(int)} does (POI 5.4.1), with the cell widths looked up first.
   */
  public void autoSizeColumn(Sheet sheet, int column) {
    if (!(sheet instanceof XSSFSheet)) {
      sheet.autoSizeColumn(column);
      return;
    }
    XSSFSheet xs = (XSSFSheet) sheet;
    List<CellRangeAddress> mergedRegions = xs.getMergedRegions();
    if (!mergedRegions.isEmpty()) {
      sheet.autoSizeColumn(column);
      return;
    }
    double width = getColumnWidth(xs, column, mergedRegions);
    if (width != -1) {
      width *= 256;
      width += xs.getArbitraryExtraWidth();
      int maxColumnWidth = 255 * 256; // The maximum column width for an individual cell is 255 characters
      if (width > maxColumnWidth) {
        width = maxColumnWidth;
      }
      xs.setColumnWidth(column, Math.toIntExact(Math.round(width)));
      xs.getColumnHelper().setColBestFit(column, true);
    }
  }

  /** {@link SheetUtil#getColumnWidth(Sheet, int, boolean)} with useMergedCells = false */
  private double getColumnWidth(Sheet sheet, int column, List<CellRangeAddress> mergedRegions) {
    DataFormatter formatter = new DataFormatter();
    float defaultCharWidth = SheetUtil.getDefaultCharWidthAsFloat(sheet.getWorkbook());
    double width = -1;
    for (int rowIdx = sheet.getFirstRowNum(); rowIdx <= sheet.getLastRowNum(); ++rowIdx) {
      Row row = sheet.getRow(rowIdx);
      if (row != null) {
        Cell cell = row.getCell(column);
        double cellWidth = cell == null ? -1 : getCellWidth(cell, defaultCharWidth, formatter, mergedRegions);
        width = Math.max(width, cellWidth);
      }
    }
    return width;
  }

  private double getCellWidth(Cell cell, float defaultCharWidth, DataFormatter formatter, List<CellRangeAddress> mergedRegions) {
    if (cell.getCellType() != CellType.STRING) {
      return SheetUtil.getCellWidth(cell, defaultCharWidth, formatter, false, mergedRegions);
    }
    RichTextString rt = cell.getRichStringCellValue();
    if (rt == null || rt.getString() == null) {
      return SheetUtil.getCellWidth(cell, defaultCharWidth, formatter, false, mergedRegions);
    }
    CellStyle style = cell.getCellStyle();
    Font font = cell.getSheet().getWorkbook().getFontAt(style.getFontIndex());
    Key key = new Key(font.getFontName(), font.getFontHeightInPoints(), font.getBold(), font.getItalic(), font.getUnderline(),
        style.getRotation(), style.getIndention(), Float.floatToIntBits(defaultCharWidth), rt.getString());
    Double w = widths.get(key);
    if (w == null) {
      w = SheetUtil.getCellWidth(cell, defaultCharWidth, formatter, false, mergedRegions);
      widths.putIfAbsent(key, w);
    }
    return w;
  }

  /** the number of distinct texts measured so far */
  public int size() {
    return widths.size();
  }
}
