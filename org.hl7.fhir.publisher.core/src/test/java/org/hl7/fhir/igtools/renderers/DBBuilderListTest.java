package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.model.core.CodeSystem;
import org.hl7.fhir.model.core.Enumerations.PublicationStatus;
import org.hl7.fhir.model.core.Patient;
import org.hl7.fhir.model.core.Resource;
import org.hl7.fhir.model.core.ValueSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DBBuilderListTest {

  private static final String[] TABLES = {"CodeSystemList", "CodeSystemListOIDs", "CodeSystemListRefs",
    "ValueSetList", "ValueSetListOIDs", "ValueSetListSystems", "ValueSetListSources", "ValueSetListRefs"};

  @TempDir
  Path dir;

  private static void addLists(DBBuilder db) {
    for (int i = 0; i < 20; i++) {
      CodeSystem cs = new CodeSystem();
      cs.setUrl("http://example.org/CodeSystem/cs" + i);
      cs.setVersion("1.0." + i);
      cs.setStatus(PublicationStatus.ACTIVE);
      cs.setName("CS" + i);
      cs.setTitle("Code System " + i);
      Set<Resource> rl = new LinkedHashSet<>();
      for (int j = 0; j < 3; j++) {
        Patient p = new Patient();
        p.setId("p" + i + "-" + j);
        rl.add(p);
      }
      db.addToCSList(1, cs, Set.of("1.2.3." + i, "1.2.4." + i), rl);

      ValueSet vs = new ValueSet();
      vs.setUrl("http://example.org/ValueSet/vs" + i);
      vs.setName("VS" + i);
      Set<Resource> vrl = new LinkedHashSet<>();
      Patient p = new Patient();
      p.setId("v" + i);
      vrl.add(p);
      if (i % 5 == 0) {
        // a reference without an id breaks the NOT NULL constraint on ValueSetListRefs.Id
        vrl.add(new Patient());
        Patient after = new Patient();
        after.setId("after" + i);
        vrl.add(after);
      }
      db.addToVSList(2, vs, Set.of("2.3.4." + i), Set.of(cs.getUrl()), Set.of("source" + i), vrl);
    }
  }

  private static List<String> dump(Connection con) throws SQLException {
    List<String> rows = new ArrayList<>();
    try (Statement stmt = con.createStatement()) {
      for (String table : TABLES) {
        try (ResultSet rs = stmt.executeQuery("select * from " + table)) {
          ResultSetMetaData md = rs.getMetaData();
          while (rs.next()) {
            StringBuilder b = new StringBuilder(table);
            for (int c = 1; c <= md.getColumnCount(); c++) {
              b.append("|").append(rs.getString(c));
            }
            rows.add(b.toString());
          }
        }
      }
    }
    rows.sort(null);
    return rows;
  }

  private DBBuilder db(String name) {
    return new DBBuilder(new File(dir.toFile(), name).getAbsolutePath(), null, null, null, new ArrayList<>());
  }

  @Test
  void sameRowsInOneTransactionAsWithAutoCommit() throws SQLException {
    DBBuilder plain = db("plain.db");
    addLists(plain);

    DBBuilder batched = db("batched.db");
    batched.startList();
    assertFalse(batched.getConnection().getAutoCommit());
    addLists(batched);
    batched.finishList();
    assertTrue(batched.getConnection().getAutoCommit(), "auto-commit should be restored");

    List<String> expected = dump(plain.getConnection());
    assertTrue(expected.size() > 200, "expected a few hundred rows, got " + expected.size());
    assertTrue(expected.contains("ValueSetListRefs|1|Patient|v0|null|Patient/v0|null"));
    // the rows after a failed insert in the same call aren't added, with or without the transaction
    assertFalse(expected.stream().anyMatch(r -> r.contains("after0")));
    assertEquals(expected, dump(batched.getConnection()));

    plain.closeUp();
    batched.closeUp();
  }

  @Test
  void rowsAreCommittedByFinishList() throws SQLException {
    String path = new File(dir.toFile(), "committed.db").getAbsolutePath();
    DBBuilder db = new DBBuilder(path, null, null, null, new ArrayList<>());
    db.startList();
    addLists(db);
    db.finishList();
    List<String> rows = dump(db.getConnection());
    db.closeUp();

    try (Connection con = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path)) {
      assertEquals(rows, dump(con));
    }
  }
}
