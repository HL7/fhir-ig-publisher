package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The SQL in a page comes from the IG: it can't run for ever, or make huge values in native memory
 */
class DBBuilderQueryLimitsTest {

  @TempDir
  Path dir;

  private DBBuilder db() {
    DBBuilder db = new DBBuilder(new File(dir.toFile(), "package.db").getAbsolutePath(), null, null, null, new ArrayList<>());
    db.setMaxQuerySeconds(2);
    return db;
  }

  private static final String FOREVER = "WITH RECURSIVE c(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM c) SELECT count(*) AS n FROM c";

  @Test
  void aQueryThatNeverEndsIsStopped() {
    DBBuilder db = db();
    long start = System.currentTimeMillis();
    SQLException e = assertThrows(SQLException.class, () -> db.executeQueryToJson(FOREVER));
    assertTrue(e.getMessage().contains("interrupt"), e.getMessage());
    assertTrue(System.currentTimeMillis() - start < 30000);
    // and so is the same query as a table
    start = System.currentTimeMillis();
    String html = db.processSQL(FOREVER);
    assertTrue(html.contains("Error processing SQL"), html);
    assertTrue(System.currentTimeMillis() - start < 30000);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SELECT length(printf('%.*c', 900000000, 'x')) AS n", "SELECT length(zeroblob(900000000)) AS n"})
  void aHugeValueIsRefused(String sql) {
    SQLException e = assertThrows(SQLException.class, () -> db().executeQueryToJson(sql));
    assertTrue(e.getMessage().contains("too big"), e.getMessage());
  }

  /** the limits are only for the page's SQL: afterwards, the database is as it was, and ordinary queries work */
  @Test
  void afterwardsQueriesStillWork() throws SQLException {
    DBBuilder db = db();
    assertThrows(SQLException.class, () -> db.executeQueryToJson(FOREVER));
    assertEquals("[{\"n\":3}]", db.executeQueryToJson("SELECT 1+2 AS n").replace(" ", ""));
    assertEquals("[{\"n\":5}]", db.executeQueryToJson("SELECT length(printf('%.*c', 5, 'x')) AS n").replace(" ", ""));
  }
}
