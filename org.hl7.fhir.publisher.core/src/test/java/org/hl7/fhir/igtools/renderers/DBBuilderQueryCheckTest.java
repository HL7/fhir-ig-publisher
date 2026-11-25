package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.hl7.fhir.exceptions.FHIRException;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The SQL in a page ({% sql %}) comes from the IG: it can only be a single query. SQLite runs a statement before the
 * driver finds it isn't a query, and ATTACH DATABASE and VACUUM INTO create the file they name, anywhere
 */
class DBBuilderQueryCheckTest {

  @TempDir
  Path dir;

  @ParameterizedTest
  @ValueSource(strings = {
    "SELECT 1",
    "  select 1",
    "-- a comment\nSELECT 1",
    "/* a comment */ SELECT 1",
    "/* one */ -- two\n /* three */ select 1",
    "WITH a AS (SELECT 1 AS x) SELECT x FROM a",
    "SELECT ';' AS x",
    "SELECT 'it''s; fine' AS x",
    "SELECT 1 AS \"a;b\"",
    "SELECT 1 AS [a;b]",
    "SELECT 1 AS `a;b`",
    "SELECT 1 -- ; not a statement",
    "SELECT 1 /* ; ATTACH DATABASE 'x' AS y */",
    "SELECT 1;",
    "SELECT 1; -- the end",
    "SELECT 1; /* the end */  "
  })
  void aSingleQueryIsRun(String sql) throws Exception {
    DBBuilder.checkQuery(sql);
    // and SQLite agrees that it's fine
    try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("test.db")); Statement st = con.createStatement()) {
      st.executeQuery(sql).close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "ATTACH DATABASE 'PLANTED' AS x",
    "attach 'PLANTED' as x",
    "VACUUM INTO 'PLANTED'",
    "-- SELECT\nATTACH DATABASE 'PLANTED' AS x",
    "/* SELECT */ VACUUM INTO 'PLANTED'",
    "/* SELECT 1; */ ATTACH 'PLANTED' AS x",
    "SELECT 1; ATTACH DATABASE 'PLANTED' AS x",
    "SELECT 1;/* */VACUUM INTO 'PLANTED'",
    "SELECT ';'; ATTACH 'PLANTED' AS x",
    "SELECT 1 AS \"a\"\"\"; ATTACH 'PLANTED' AS x",
    "SELECT 1 -- comment\n; ATTACH 'PLANTED' AS x",
    "SELECTED 1",
    "PRAGMA writable_schema = 1",
    "DELETE FROM x",
    "",
    "-- nothing but a comment"
  })
  void anythingElseIsRefused(String sql) {
    String s = sql.replace("PLANTED", dir.resolve("planted.db").toString());
    assertThrows(FHIRException.class, () -> DBBuilder.checkQuery(s));
    assertFalse(Files.exists(dir.resolve("planted.db")));
  }
}
