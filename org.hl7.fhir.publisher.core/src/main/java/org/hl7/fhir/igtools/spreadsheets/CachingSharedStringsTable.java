package org.hl7.fhir.igtools.spreadsheets;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.ooxml.POIXMLRelation;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.usermodel.XSSFFactory;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlCursor.TokenType;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRst;

/**
 * POI's shared strings table, with a cheaper lookup for strings it has seen before.
 * <p>
 * POI finds an existing entry by serialising each new string to XML and using that as the key, and that
 * serialisation is most of the cost of setting a cell value. This table puts a map in front of POI's own,
 * keyed on the text and the {@code xml:space} attribute of a string that is nothing but a {@code <t>} element:
 * all that serialisation is made of, for such a string. When the key is known, POI would find the
 * same entry, so the table just counts the use and returns that entry. Everything else, including every first
 * use of a string, goes to POI, so the entries, their order and the counts are all exactly POI's.
 */
public class CachingSharedStringsTable extends SharedStringsTable {

  private record Plain(String text, boolean preserveSpace) {
  }

  private static final QName T = new QName(XSSFRelation.NS_SPREADSHEETML, "t");
  private static final QName XML_SPACE = new QName("http://www.w3.org/XML/1998/namespace", "space");

  private final Map<Plain, Integer> known = new HashMap<>();

  private static final XSSFFactory FACTORY = new XSSFFactory() {
    @Override
    public POIXMLDocumentPart newDocumentPart(POIXMLRelation descriptor) {
      return descriptor == XSSFRelation.SHARED_STRINGS ? new CachingSharedStringsTable() : super.newDocumentPart(descriptor);
    }
  };

  /** a new, empty workbook, the same as {@code new XSSFWorkbook()}, that uses this table for its shared strings */
  public static XSSFWorkbook newWorkbook() {
    return new XSSFWorkbook(FACTORY);
  }

  @Override
  public int addSharedStringItem(RichTextString string) {
    Plain key = string instanceof XSSFRichTextString ? plain(((XSSFRichTextString) string).getCTRst()) : null;
    if (key == null) {
      return super.addSharedStringItem(string);
    }
    Integer index = known.get(key);
    if (index != null) {
      count++; // as POI does for a string it already has
      return index;
    }
    int res = super.addSharedStringItem(string);
    known.put(key, res);
    return res;
  }

  /** the number of distinct strings known to the cache */
  int size() {
    return known.size();
  }

  /**
   * the key for a string that is a fragment of its own holding only a {@code <t>} element (with no attribute
   * other than {@code xml:space="preserve"}), as {@code new XSSFRichTextString(String)} makes them; null for
   * anything else
   */
  private static Plain plain(CTRst st) {
    try (XmlCursor c = st.newCursor()) {
      if (!c.isStartdoc() || !c.toNextToken().isStart() || !T.equals(c.getName())) {
        return null;
      }
      TokenType token = c.toNextToken();
      boolean preserve = false;
      if (token.isAttr()) {
        if (!XML_SPACE.equals(c.getName()) || !"preserve".equals(c.getTextValue())) {
          return null;
        }
        preserve = true;
        token = c.toNextToken();
      }
      String text = "";
      if (token.isText()) {
        text = c.getChars();
        token = c.toNextToken();
      }
      if (!token.isEnd() || !c.toNextToken().isEnddoc()) {
        return null;
      }
      return new Plain(text, preserve);
    }
  }
}
