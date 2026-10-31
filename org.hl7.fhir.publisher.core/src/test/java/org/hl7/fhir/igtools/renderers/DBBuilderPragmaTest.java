package org.hl7.fhir.igtools.renderers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * package.db is written with the rollback journal in memory and without fsync. That must not change the file:
 * the same statements give the same bytes as SQLite's defaults, including after a rollback.
 */
class DBBuilderPragmaTest {

  private static void build(Path file, boolean configured) throws SQLException {
    try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + file)) {
      if (configured) {
        DBBuilder.configureForBuildArtifact(con);
      }
      try (Statement st = con.createStatement()) {
        st.execute("CREATE TABLE Resources (Key integer NOT NULL, Name nvarchar NULL, Json blob NULL, PRIMARY KEY (Key))");
        st.execute("CREATE TABLE Refs (Key integer NOT NULL, Ref nvarchar NULL)");
      }
      // auto-commit inserts, one transaction each, as saveResource and addToVSList do
      for (int i = 1; i <= 300; i++) {
        try (PreparedStatement ps = con.prepareStatement("insert into Resources (Key, Name, Json) values (?, ?, ?)")) {
          ps.setInt(1, i);
          ps.setString(2, "resource " + i);
          ps.setBytes(3, new byte[(i * 37) % 9000]);
          ps.executeUpdate();
        }
      }
      // a batch in a transaction that is committed, and one that is rolled back, as finishResources does
      con.setAutoCommit(false);
      try (PreparedStatement ps = con.prepareStatement("insert into Refs (Key, Ref) values (?, ?)")) {
        for (int i = 0; i < 2000; i++) {
          ps.setInt(1, i);
          ps.setString(2, "http://example.org/ref/" + i);
          ps.addBatch();
        }
        ps.executeBatch();
      }
      con.commit();
      try (PreparedStatement ps = con.prepareStatement("insert into Refs (Key, Ref) values (?, ?)")) {
        for (int i = 0; i < 500; i++) {
          ps.setInt(1, -i);
          ps.setString(2, "rolled back " + i);
          ps.addBatch();
        }
        ps.executeBatch();
      }
      con.rollback();
      con.setAutoCommit(true);
    }
  }

  @Test
  void sameFileAsWithSqliteDefaults(@TempDir Path dir) throws SQLException, IOException {
    Path a = dir.resolve("default.db");
    Path b = dir.resolve("configured.db");
    build(a, false);
    build(b, true);
    assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b));
    try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + b); Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("select count(*), min(Key) from Refs")) {
      rs.next();
      assertEquals(2000, rs.getInt(1));
      assertEquals(0, rs.getInt(2));
    }
  }

  @Test
  void packageDbIsBuiltWithTheseSettings(@TempDir Path dir) throws SQLException {
    Path db = dir.resolve("package.db");
    DBBuilder builder = new DBBuilder(db.toString(), null, null, null, null);
    builder.metadata("name", "value");
    try (Statement st = builder.getConnection().createStatement()) {
      try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
        rs.next();
        assertEquals("memory", rs.getString(1));
      }
      try (ResultSet rs = st.executeQuery("select name, value from Metadata")) {
        rs.next();
        assertEquals("name", rs.getString(1));
        assertEquals("value", rs.getString(2));
      }
    }
    builder.closeUp();
    assertEquals(false, Files.exists(dir.resolve("package.db-journal")));
  }

  @Test
  void settingsApplied(@TempDir Path dir) throws SQLException {
    try (Connection con = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("p.db"))) {
      DBBuilder.configureForBuildArtifact(con);
      try (Statement st = con.createStatement()) {
        try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
          rs.next();
          assertEquals("memory", rs.getString(1));
        }
        try (ResultSet rs = st.executeQuery("PRAGMA synchronous")) {
          rs.next();
          assertEquals(0, rs.getInt(1));
        }
      }
    }
    assertEquals(false, Files.exists(dir.resolve("p.db-journal")));
  }
}
