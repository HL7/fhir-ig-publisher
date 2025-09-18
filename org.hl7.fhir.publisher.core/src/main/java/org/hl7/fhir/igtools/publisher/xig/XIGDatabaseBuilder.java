package org.hl7.fhir.igtools.publisher.xig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.convertors.analytics.PackageVisitor.IPackageVisitorProcessor;
import org.hl7.fhir.convertors.analytics.PackageVisitor.PackageContext;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.dstu3.model.Composition;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.conformance.profile.ProfileUtilities;
import org.hl7.fhir.r5.extensions.ExtensionDefinitions;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceOperationComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.ElementDefinition;
import org.hl7.fhir.r5.model.ElementDefinition.ElementDefinitionBindingAdditionalComponent;
import org.hl7.fhir.r5.model.ElementDefinition.TypeRefComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionContextComponent;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionParameterComponent;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.FileUtilities;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;

public class XIGDatabaseBuilder implements IPackageVisitorProcessor {


  private Connection con;

  private PreparedStatement sqlInsertPackage;
  private int pckKey;
  private PreparedStatement sqlInsertResource;
  private PreparedStatement sqlUpdateResource;
  private PreparedStatement psqlCat;
  private int resKey;
  private int docKey;
  private PreparedStatement sqlInsertContents;
  private PreparedStatement sqlUpdateContents;
  private PreparedStatement sqlAddDocument;
  private PreparedStatement sqlAddDocumentProfile;
  private PreparedStatement sqlAddDocumentCode;
  private PreparedStatement psqlRI;
  private PreparedStatement psqlCI;
  private PreparedStatement psqlDep;
  private PreparedStatement psqlExtnUrl;
  private PreparedStatement psqlExtnUser;
  private PreparedStatement psqlExtnUse;
  private Set<String> vurls = new HashSet<>();
  private int lastMDKey;
  private Set<String> authorities = new HashSet<>();
  private Set<String> realms = new HashSet<>();
  private int pck;
  private Set<String> resourceTypes = new HashSet<>();
  private Map<String, Integer> extensionUsers = new HashedMap<>();
  private Map<String, Integer> extensionUrls = new HashedMap<>();

  public XIGDatabaseBuilder(String dest, boolean init, String date) throws IOException {
    super();
    try {
      System.out.println("Product XIG at "+dest);
      con = connect(dest, init, date);

      sqlInsertPackage = con.prepareStatement("Insert into Packages (PackageKey, PID, Id, Date, Title, Canonical, Web, Version, R2, R2B, R3, R4, R4B, R5, R6, Realm, Auth, Package, Published) Values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)");
      sqlInsertResource = con.prepareStatement("Insert into Resources (ResourceKey, PackageKey, ResourceType, Id, R2, R2B, R3, R4, R4B, R5, R6) Values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sqlUpdateResource = con.prepareStatement("Update Resources set ResourceTypeR5 = ?, Web = ?, Url = ?, Version = ?, Status = ?, Date = ?, Name = ?, Title = ?, Experimental = ?, Realm = ?, Description = ?, Purpose = ?, Copyright = ?, CopyrightLabel = ?, "+
              "Kind = ?, Type = ?, Supplements = ?, ValueSet = ?, Content = ?, Authority = ?, Details = ?, StandardsStatus = ?, FMM = ?, WG = ? where ResourceKey = ?");
      sqlInsertContents = con.prepareStatement("Insert into Contents (ResourceKey, Json) Values (?, ?)");
      sqlUpdateContents = con.prepareStatement("Update Contents set JsonR5 = ? where ResourceKey = ?");
      sqlAddDocument = con.prepareStatement("insert into Documents (DocumentKey, ResourceKey, Instance) values (?, ?, ?)");
      sqlAddDocumentProfile = con.prepareStatement("insert into DocumentProfiles (DocumentKey, Profile) values (?, ?)");
      sqlAddDocumentCode = con.prepareStatement("insert into DocumentCodes (DocumentKey, System, Version, Code, Display) values (?, ?, ?, ?, ?)");

      psqlCat = con.prepareStatement("Insert into Categories (ResourceKey, Mode, Code) Values (?, ?, ?)");
      psqlRI = con.prepareStatement("Insert into ResourceFTS (ResourceKey, Name, Title, Description, Narrative) Values (?, ?, ?, ?, ?)");
      psqlCI = con.prepareStatement("Insert into CodeSystemFTS (ResourceKey, Code, Display, Definition) Values (?, ?, ?, ?)");
      psqlDep = con.prepareStatement("Insert into DependencyTemp (TargetUrl, SourceKey) Values (?, ?)");
      psqlExtnUrl = con.prepareStatement("Insert into ExtensionDefns (ExtensionDefnKey, Url) Values (?, ?)");
      psqlExtnUser = con.prepareStatement("Insert into ExtensionUsers (ExtensionUserKey, Url, Name, Version) Values (?, ?, ?, ?)");
      psqlExtnUse = con.prepareStatement("Insert into ExtensionUsages (ExtensionDefnKey, ExtensionUserKey) Values (?, ?)");
    } catch (Exception e) {
      throw new IOException(e);
    }
  }


  private Connection connect(String filename, boolean init, String date) throws IOException, SQLException {
    Connection con = DriverManager.getConnection("jdbc:sqlite:"+filename);
    if (init) {
      makeMetadataTable(con);
      makePackageTable(con);
      makeResourcesTable(con);
      makeContentsTable(con);
      makeRealmsTable(con);
      makeAuthoritiesTable(con);
      makeCategoriesTable(con);
      makeResourceIndex(con);
      makeCodeIndex(con);
      makeTxSourceList(con);
      makeDependencyTable(con);
      makeExtensionDefnTable(con);
      makeExtensionUserTable(con);
      makeExtensionUsageTable(con);
      makeDocumentsTable(con);
      makeDocumentProfilesTable(con);
      makeDocumentCodesTable(con);
      PreparedStatement psql = con.prepareStatement("Insert into Metadata (key, name, value) values (?, ?, ?)");
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "date");
      psql.setString(3, date);
      psql.executeUpdate();
    } else {

      ResultSet rs = con.createStatement().executeQuery("select * from Realms");
      while (rs.next()) {
        realms.add(rs.getString(1));
      }
      rs = con.createStatement().executeQuery("select * from Authorities");
      while (rs.next()) {
        authorities.add(rs.getString(1));
      }
      rs = con.createStatement().executeQuery("select * from Metadata");
      while (rs.next()) {
        lastMDKey = rs.getInt(1);
        if ("pckKey".equals(rs.getString(2))) {
          pckKey = rs.getInt(3);
        }
        if ("resKey".equals(rs.getString(2))) {
          resKey = rs.getInt(3);
        }
        if ("docKey".equals(rs.getString(2))) {
          docKey = rs.getInt(3);
        }
        if ("totalpackages".equals(rs.getString(2))) {
          pck = rs.getInt(3);
        }
      }
      con.createStatement().execute("delete from Metadata");
      con.createStatement().execute("delete from Realms");
      con.createStatement().execute("delete from Authorities");
      PreparedStatement psql = con.prepareStatement("Insert into Metadata (key, name, value) values (?, ?, ?)");
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "date");
      psql.setString(3, date);
      psql.executeUpdate();
      rs = con.createStatement().executeQuery("select ExtensionUserKey, Url from ExtensionUsers");
      while (rs.next()) {
        extensionUsers.put(rs.getString("Url"), rs.getInt("ExtensionUserKey"));
      }
      rs = con.createStatement().executeQuery("select ExtensionDefnKey, Url from ExtensionDefns");
      while (rs.next()) {
        extensionUrls.put(rs.getString("Url"), rs.getInt("ExtensionDefnKey"));
      }
      rs = con.createStatement().executeQuery("select ExtensionDefnKey, ExtensionUserKey from ExtensionUsages");
      while (rs.next()) {
        String k = "" + rs.getString("ExtensionDefnKey") + ":" + rs.getString("ExtensionUserKey");
        XIGExtensionUsageProcessor.used.add(k);
      }
    }
    return con;    
  }

  private void makeExtensionDefnTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ExtensionDefns (\r\n"+
            "ExtensionDefnKey         integer NOT NULL,\r\n"+
            "Url                      nvarchar NOT NULL,\r\n"+
            "PRIMARY KEY (ExtensionDefnKey))\r\n");
    stmt.execute("CREATE INDEX SK_ExtensionDefns  on ExtensionDefns (Url)\r\n");
  }

  private void makeExtensionUserTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ExtensionUsers (\r\n"+
            "ExtensionUserKey         integer NOT NULL,\r\n"+
            "Url                      nvarchar NOT NULL,\r\n"+
            "Name                     nvarchar NOT NULL,\r\n"+
            "Version                  integer NOT NULL,\r\n"+
            "PRIMARY KEY (ExtensionUserKey))\r\n");
  }


  private void makeExtensionUsageTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ExtensionUsages (\r\n"+
            "ExtensionDefnKey         integer NOT NULL,\r\n"+
            "ExtensionUserKey         integer NOT NULL,\r\n"+
            "PRIMARY KEY (ExtensionDefnKey, ExtensionUserKey))\r\n");
  }

  private void makeDocumentsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Documents (\r\n"+
            "DocumentKey         integer NOT NULL,\r\n"+
            "ResourceKey         integer NOT NULL,\r\n"+
            "Instance            integer NOT NULL,\r\n"+
            "PRIMARY KEY (DocumentKey))\r\n");
  }

  private void makeDocumentProfilesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE DocumentProfiles (\r\n"+
            "DocumentKey         integer NOT NULL,\r\n"+
            "Profile             nvarchar NULL,\r\n"+
            "PRIMARY KEY (DocumentKey, Profile))\r\n");
  }

  private void makeDocumentCodesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE DocumentCodes (\r\n"+
            "DocumentKey         integer NOT NULL,\r\n"+
            "System              nvarchar NOT NULL,\r\n"+
            "Code                nvarchar NOT NULL,\r\n"+
            "Display             nvarchar NULL,\r\n"+
            "Version             nvarchar NULL,\r\n"+
            "PRIMARY KEY (DocumentKey, System, Code))\r\n");
  }

  private void makeDependencyTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE DependencyTemp (\r\n"+
        "TargetUrl         nvarchar NOT NULL,\r\n"+
        "SourceKey            integer NOT NULL,\r\n"+
        "PRIMARY KEY (TargetUrl, SourceKey))\r\n"); 
    stmt.execute("CREATE TABLE DependencyList (\r\n"+
        "TargetKey         integer NOT NULL,\r\n"+
        "SourceKey         integer NOT NULL,\r\n"+
        "PRIMARY KEY (TargetKey, SourceKey))\r\n"); 
  }

  private void makeMetadataTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Metadata (\r\n"+
        "Key    integer NOT NULL,\r\n"+
        "Name   nvarchar NOT NULL,\r\n"+
        "Value  nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeResourceIndex(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE VIRTUAL TABLE ResourceFTS USING fts5(ResourceKey, Name, Title, Description, Narrative)");
  }


  private void makeCodeIndex(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE VIRTUAL TABLE CodeSystemFTS USING fts5(ResourceKey, Code, Display, Definition)");
  }

  private void makePackageTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Packages (\r\n"+
        "PackageKey integer NOT NULL,\r\n"+
        "PID        nvarchar NOT NULL,\r\n"+
        "Id         nvarchar NOT NULL,\r\n"+
        "Date       nvarchar NULL,\r\n"+
        "Title      nvarchar NULL,\r\n"+
        "Canonical  nvarchar NULL,\r\n"+
        "Web        nvarchar NULL,\r\n"+
        "Version    nvarchar NULL,\r\n"+
        "Published  INTEGER NULL,\r\n"+
        "R2         INTEGER NULL,\r\n"+
        "R2B        INTEGER NULL,\r\n"+
        "R3         INTEGER NULL,\r\n"+
        "R4         INTEGER NULL,\r\n"+
        "R4B        INTEGER NULL,\r\n"+
        "R5         INTEGER NULL,\r\n"+
        "R6         INTEGER NULL,\r\n"+
        "Realm      nvarchar NULL,\r\n"+
        "Auth       nvarchar NULL,\r\n"+
        "Package    BLOB NULL,\r\n"+
        "PRIMARY KEY (PackageKey))\r\n");
  }


  private void makeResourcesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Resources (\r\n"+
        "ResourceKey     integer NOT NULL,\r\n"+
        "PackageKey      integer NOT NULL,\r\n"+
        "ResourceType    nvarchar NOT NULL,\r\n"+
        "ResourceTypeR5  nvarchar NULL,\r\n"+
        "Id              nvarchar NOT NULL,\r\n"+
        "R2              INTEGER NULL,\r\n"+
        "R2B             INTEGER NULL,\r\n"+
        "R3              INTEGER NULL,\r\n"+
        "R4              INTEGER NULL,\r\n"+
        "R4B             INTEGER NULL,\r\n"+
        "R5              INTEGER NULL,\r\n"+
        "R6              INTEGER NULL,\r\n"+
        "Core            INTEGER NULL,\r\n"+
        "Web             nvarchar NULL,\r\n"+
        "Url             nvarchar NULL,\r\n"+
        "Version         nvarchar NULL,\r\n"+
        "Status          nvarchar NULL,\r\n"+
        "Date            nvarchar NULL,\r\n"+
        "Name            nvarchar NULL,\r\n"+
        "Title           nvarchar NULL,\r\n"+
        "Experimental    INTEGER NULL,\r\n"+
        "Realm           nvarchar NULL,\r\n"+
        "Authority       nvarchar NULL,\r\n"+
        "Description     nvarchar NULL,\r\n"+
        "Purpose         nvarchar NULL,\r\n"+
        "Copyright       nvarchar NULL,\r\n"+
        "CopyrightLabel  nvarchar NULL,\r\n"+
        "Content         nvarchar NULL,\r\n"+
        "Type            nvarchar NULL,\r\n"+
        "Supplements     nvarchar NULL,\r\n"+
        "ValueSet        nvarchar NULL,\r\n"+
        "Kind            nvarchar NULL,\r\n"+
        "Details         nvarchar NULL,\r\n"+
        "StandardsStatus nvarchar NULL,\r\n"+
        "FMM             nvarchar NULL,\r\n"+
        "WG              nvarchar NULL,\r\n"+
        "PRIMARY KEY (ResourceKey))\r\n");

    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_type_kind_type ON Resources(ResourceType, Kind, Type)");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_packagekey ON Resources(PackageKey)");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_resourcetype ON Resources(ResourceType)");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_realm ON Resources(Realm)");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_authority ON Resources(Authority)");

    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_r2 ON Resources(ResourceType, Kind) WHERE R2 = 1");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_r2b ON Resources(ResourceType, Kind) WHERE R2B = 1");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_r3 ON Resources(ResourceType, Kind) WHERE R3 = 1");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_r4 ON Resources(ResourceType, Kind) WHERE R4 = 1");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_r4b ON Resources(ResourceType, Kind) WHERE R4B = 1");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_r5 ON Resources(ResourceType, Kind) WHERE R5 = 1");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_r6 ON Resources(ResourceType, Kind) WHERE R6 = 1");

    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_realm_auth_type ON Resources(Realm, Authority, ResourceType)");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_package_id ON Resources(PackageKey, ResourceType, Id)");

    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_supplements ON Resources(Supplements) WHERE Supplements IS NOT NULL");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_url ON Resources(Url) WHERE Url IS NOT NULL");
    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_stats ON Resources(ResourceType, Kind, Realm, Authority)");

    stmt.execute("CREATE INDEX IF NOT EXISTS idx_resources_covering ON Resources(" +
                    "ResourceType, Kind, Type, PackageKey, Realm, Authority, " +
                    "Id, Url, Version, Status, Date, Name, Title, " +
                    "Description, FMM, WG, StandardsStatus)");
  }


  private void makeContentsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Contents (\r\n"+
        "ResourceKey     integer NOT NULL,\r\n"+
        "Json            BLOB NOT NULL,\r\n"+
        "JsonR5          BLOB NULL,\r\n"+
        "PRIMARY KEY (ResourceKey))\r\n");
  }

  private void makeCategoriesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Categories (\r\n"+
        "ResourceKey   integer  NOT NULL,\r\n"+
        "Mode          integer  NOT NULL,\r\n"+
        "Code          nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (ResourceKey, Mode, Code))\r\n");
  }

  private void makeRealmsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Realms (\r\n"+
        "Code            nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (Code))\r\n");
  }

  private void makeAuthoritiesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Authorities (\r\n"+
        "Code            nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (Code))\r\n"); 
  }

  private void makeTxSourceList(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE TxSource (\r\n"+
        "Code            nvarchar NOT NULL,\r\n"+
        "Display         nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (Code))\r\n"); 
    defineTxSources(stmt);
  }

  public void finish(boolean finalFinish) throws IOException {
    try {
      if (finalFinish) {
        con.createStatement().execute("insert into DependencyList (TargetKey, SourceKey) select ResourceKey as TargetKey, SourceKey from DependencyTemp, Resources where Resources.URL = DependencyTemp.targetUrl");
        con.createStatement().execute("delete from DependencyTemp");
        con.createStatement().execute("drop Table DependencyTemp");
      }
      
      con.createStatement().execute("delete from Realms");
      PreparedStatement psql = con.prepareStatement("Insert into Realms (code) values (?)");
      for (String s : realms) {
        psql.setString(1, s);
        psql.executeUpdate();
      }
      
      con.createStatement().execute("delete from Authorities");
      psql = con.prepareStatement("Insert into Authorities (code) values (?)");
      for (String s : authorities) {
        psql.setString(1, s);
        psql.executeUpdate();
      }

      Statement stmt = con.createStatement();
      stmt.execute("Select Count(*) from Packages");
      ResultSet res = stmt.getResultSet();
      res.next();
      int packages = res.getInt(1);
      stmt = con.createStatement();
      stmt.execute("Select Count(*) from Packages where published = 1");
      res = stmt.getResultSet();
      res.next();
      int ppackages = res.getInt(1);
      stmt = con.createStatement();
      stmt.execute("Select Count(*) from Resources");
      res = stmt.getResultSet();
      res.next();
      int resources = res.getInt(1);

      psql = con.prepareStatement("Insert into Metadata (key, name, value) values (?, ?, ?)");
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "realms");
      psql.setString(3, ""+realms.size());
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "authorities");
      psql.setString(3, ""+authorities.size());
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "packages");
      psql.setString(3, ""+packages);
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "pubpackages");
      psql.setString(3, ""+ppackages);
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "resources");
      psql.setString(3, ""+resources);
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "totalpackages");
      psql.setString(3, ""+pck);
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "pckKey");
      psql.setString(3, ""+pckKey);
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "resKey");
      psql.setString(3, ""+resKey);
      psql.executeUpdate();
      psql.setInt(1, ++lastMDKey);
      psql.setString(2, "docKey");
      psql.setString(3, ""+docKey);
      psql.executeUpdate();

      con.close();
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public Object startPackage(PackageContext context) throws IOException {
    if (isCoreDefinition(context.getPid())) {
      return null;
    }
    try {
      String pid = context.getPid();
      NpmPackage npm = context.getNpm();
      SpecMapManager smm = npm.hasFile("other", "spec.internals") ?  new SpecMapManager( FileUtilities.streamToBytes(npm.load("other", "spec.internals")), npm.name(), npm.fhirVersion()) : SpecMapManager.createSpecialPackage(npm, null);
      pckKey++;
      smm.setName(npm.name());
      smm.setBase(npm.canonical());
      smm.setBase2(PackageHacker.fixPackageUrl(npm.url()));
      smm.setKey(pckKey);

      String auth = getAuth(pid, null);
      String realm = getRealm(pid, null);
      smm.setAuth(auth);
      smm.setRealm(realm);

      sqlInsertPackage.setInt(1, pckKey);
      sqlInsertPackage.setString(2, pid);
      sqlInsertPackage.setString(3, npm.name());
      sqlInsertPackage.setString(4, npm.date());
      sqlInsertPackage.setString(5, npm.title());
      sqlInsertPackage.setString(6, npm.canonical());
      sqlInsertPackage.setString(7, npm.getWebLocation());
      sqlInsertPackage.setString(8, npm.version());
      sqlInsertPackage.setInt(9, hasVersion(npm.fhirVersionList(), "1.0"));
      sqlInsertPackage.setInt(10, hasVersion(npm.fhirVersionList(), "1.4"));
      sqlInsertPackage.setInt(11, hasVersion(npm.fhirVersionList(), "3.0"));
      sqlInsertPackage.setInt(12, hasVersion(npm.fhirVersionList(), "4.0"));
      sqlInsertPackage.setInt(13, hasVersion(npm.fhirVersionList(), "4.3"));
      sqlInsertPackage.setInt(14, hasVersion(npm.fhirVersionList(), "5.0"));
      sqlInsertPackage.setInt(15, hasVersion(npm.fhirVersionList(), "6.0"));
      sqlInsertPackage.setString(16, realm);
      sqlInsertPackage.setString(17, auth);
      sqlInsertPackage.setBytes(18, org.hl7.fhir.utilities.json.parser.JsonParser.composeBytes(npm.getNpm()));
      sqlInsertPackage.execute();
      pck++;

      return smm;

    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public void processResource(PackageContext context, Object clientContext, String type, String id, byte[] content) throws FHIRException, IOException, EOperationOutcome {   
    if (clientContext != null) {
      SpecMapManager smm = (SpecMapManager) clientContext;

      try {
        resKey++;
        sqlInsertResource.setInt(1, resKey);
        sqlInsertResource.setInt(2, smm.getKey());
        sqlInsertResource.setString(3, type);
        sqlInsertResource.setString(4, id);
        sqlInsertResource.setInt(5, hasVersion(context.getVersion(), "1.0"));
        sqlInsertResource.setInt(6, hasVersion(context.getVersion(), "1.4"));
        sqlInsertResource.setInt(7, hasVersion(context.getVersion(), "3.0"));
        sqlInsertResource.setInt(8, hasVersion(context.getVersion(), "4.0"));
        sqlInsertResource.setInt(9, hasVersion(context.getVersion(), "4.3"));
        sqlInsertResource.setInt(10, hasVersion(context.getVersion(), "5.0"));
        sqlInsertResource.setInt(11, hasVersion(context.getVersion(), "6.0"));
        sqlInsertResource.execute();

        sqlInsertContents.setInt(1, resKey);
        sqlInsertContents.setBytes(2, gzip(content));
        sqlInsertContents.execute();

        Resource r = loadResource(context.getPid(), context.getVersion(), type, id, content, smm);
        if (r != null && resourceTypes.contains(r.fhirType())) {
          String auth = smm.getAuth();
          String realm = smm.getRealm();

          if (r != null && r instanceof CanonicalResource) {
            CanonicalResource cr = (CanonicalResource) r;
            if (!vurls.contains(cr.getUrl())) {
              vurls.add(cr.getUrl());
              if (realm == null) {
                realm = getRealm(context.getPid(), cr);
                if (realm != null) {
                  smm.setRealm(realm);
                  Statement stmt = con.createStatement();
                  stmt.execute("update Packages set realm = '" + realm + "' where PackageKey = " + smm.getKey());
                }
              }
              if (auth == null) {
                auth = getAuth(context.getPid(), cr);
                if (auth != null) {
                  smm.setAuth(auth);
                  Statement stmt = con.createStatement();
                  stmt.execute("update Packages set auth = '" + auth + "' where PackageKey = " + smm.getKey());
                }
              }

              JsonObject j = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(content);
              String narrative = cr.getText().getDiv().allText();
              cr.setText(null);

              String details = null;

              Set<String> dependencies = new HashSet<>();
              ;

              if (cr instanceof CodeSystem) {
                details = "" + processCodesystem(resKey, (CodeSystem) cr, dependencies);
              }
              if (cr instanceof ValueSet) {
                details = processValueSet(resKey, (ValueSet) cr, context.getNpm(), dependencies);
              }
              if (cr instanceof ConceptMap) {
                details = processConceptMap(resKey, (ConceptMap) cr, context.getNpm(), dependencies);
              }
              if (cr instanceof StructureDefinition) {
                details = processStructureDefinition(resKey, (StructureDefinition) cr, context.getNpm(), dependencies);
              }
              if (cr instanceof CapabilityStatement) {
                details = processCapabilityStatement(resKey, (CapabilityStatement) cr, context.getNpm(), dependencies);
              }

              String web = smm.getPath(cr.getUrl(), null, cr.fhirType(), cr.getIdBase());
              if (web != null && !Utilities.isAbsoluteUrl(web)) {
                web = Utilities.pathURL(smm.getBase(), web);
              }
              String rid = r.hasId() ? r.getId() : id.replace(".json", "");
              sqlUpdateResource.setString(1, r.fhirType());
              sqlUpdateResource.setString(2, web);
              sqlUpdateResource.setString(3, cr.getUrl());
              sqlUpdateResource.setString(4, cr.getVersion());
              sqlUpdateResource.setString(5, cr.getStatus().toCode());
              sqlUpdateResource.setString(6, cr.getDateElement().primitiveValue());
              sqlUpdateResource.setString(7, cr.getName());
              sqlUpdateResource.setString(8, cr.getTitle());
              sqlUpdateResource.setBoolean(9, cr.getExperimental());
              sqlUpdateResource.setString(10, realm);
              sqlUpdateResource.setString(11, cr.getDescription());
              sqlUpdateResource.setString(12, cr.getPurpose());
              sqlUpdateResource.setString(13, cr.getCopyright());
              sqlUpdateResource.setString(14, cr.getCopyrightLabel());
              sqlUpdateResource.setString(15, j.asString("kind"));
              sqlUpdateResource.setString(16, j.asString("type"));
              sqlUpdateResource.setString(17, j.asString("supplements"));
              sqlUpdateResource.setString(18, j.asString("valueSet"));
              sqlUpdateResource.setString(19, j.asString("content"));
              sqlUpdateResource.setString(20, auth);
              sqlUpdateResource.setString(21, details);
              sqlUpdateResource.setString(22, ExtensionUtilities.readStringExtension(cr, ExtensionDefinitions.EXT_STANDARDS_STATUS));
              sqlUpdateResource.setString(23, ExtensionUtilities.readStringExtension(cr, ExtensionDefinitions.EXT_FMM_LEVEL));
              sqlUpdateResource.setString(24, ExtensionUtilities.readStringExtension(cr, ExtensionDefinitions.EXT_WORKGROUP));
              sqlUpdateResource.setInt(25, resKey);
              sqlUpdateResource.execute();

              sqlUpdateContents.setBytes(1, gzip(new JsonParser().composeBytes(cr)));
              sqlUpdateContents.setInt(2, resKey);
              sqlUpdateContents.execute();

              psqlRI.setInt(1, resKey);
              psqlRI.setString(2, cr.getName());
              psqlRI.setString(3, cr.getTitle());
              psqlRI.setString(4, cr.getDescription());
              psqlRI.setString(5, narrative);
              psqlRI.execute();

              //            if (cr instanceof StructureDefinition) {
              //              dep = processStructureDefinition(resKey, (StructureDefinition) cr);
              //              ext = processStructureDefinition2(resKey, (StructureDefinition) cr);
              //            }
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new IOException(e);
      }
    }
  }

  private int processCodesystem(int resKey, CodeSystem cs, Set<String> dependencies) throws SQLException {
    if (cs.hasSupplements()) {
      recordDependency(dependencies, resKey, cs.getSupplements());
    }
    return processCodes(cs.getConcept());
  }

  private void recordDependency(Set<String> dependencies, int resKey, String url) throws SQLException {
    if (Utilities.isAbsoluteUrl(url)) {
      if (url.contains("|")) {
        url = url.substring(0, url.indexOf("|"));
      }
      if (!isCore(url) && !dependencies.contains(url)) {
        dependencies.add(url);
        psqlDep.setString(1, url);
        psqlDep.setInt(2, resKey);
        psqlDep.execute();
      }
    }
  }

  private String processCapabilityStatement(int resKey, CapabilityStatement cs, NpmPackage npm, Set<String> dependencies) throws SQLException {

    for (CanonicalType cr : cs.getInstantiates()) {
      recordDependency(dependencies, resKey, cr.asStringValue());
    }
    for (CanonicalType cr : cs.getImports()) {
      recordDependency(dependencies, resKey, cr.asStringValue());
    }
    for (CanonicalType cr : cs.getImplementationGuide()) {
      recordDependency(dependencies, resKey, cr.asStringValue());
    }
    for (CapabilityStatementRestComponent r : cs.getRest()) {
      for (CapabilityStatementRestResourceComponent res : r.getResource()) {
        recordDependency(dependencies, resKey, res.getProfile());
        for (CanonicalType cr : res.getSupportedProfile()) {
          recordDependency(dependencies, resKey, cr.asStringValue());
        }
        for (CapabilityStatementRestResourceSearchParamComponent sp : res.getSearchParam()) {
          recordDependency(dependencies, resKey, sp.getDefinition());
        }
        for (CapabilityStatementRestResourceOperationComponent op : res.getOperation()) {
          recordDependency(dependencies, resKey, op.getDefinition());
        }
      }
      for (CapabilityStatementRestResourceSearchParamComponent sp : r.getSearchParam()) {
        recordDependency(dependencies, resKey, sp.getDefinition());
      }
      for (CapabilityStatementRestResourceOperationComponent op : r.getOperation()) {
        recordDependency(dependencies, resKey, op.getDefinition());
      }
    }
    return null;
  }

  private String processStructureDefinition(int resKey, StructureDefinition sd, NpmPackage npm, Set<String> dependencies) throws SQLException {
    recordDependency(dependencies, resKey, sd.getBaseDefinition());
    for (ElementDefinition ed : sd.getDifferential().getElement()) {
      if (ed.hasBinding()) {
        recordDependency(dependencies, resKey, ed.getBinding().getValueSet());
        for (ElementDefinitionBindingAdditionalComponent ab : ed.getBinding().getAdditional()) {
          recordDependency(dependencies, resKey, ab.getValueSet());            
        }
      }
      for (TypeRefComponent tr : ed.getType()) {
        recordDependency(dependencies, resKey, tr.getWorkingCode());
        for (CanonicalType cr : tr.getProfile()) {
          recordDependency(dependencies, resKey, cr.asStringValue());
        }
        for (CanonicalType cr : tr.getTargetProfile()) {
          recordDependency(dependencies, resKey, cr.asStringValue());
        }
      }
      for (CanonicalType cr : ed.getValueAlternatives()) {
        recordDependency(dependencies, resKey, cr.asStringValue());
      }
    }

    if (ProfileUtilities.isExtensionDefinition(sd)) {
      return processExtensionDefinition(resKey, sd);
    } else {
      return null;
    }
  }

  private String processExtensionDefinition(int resKey, StructureDefinition sd) throws SQLException {
    Set<String> tset = new HashSet<>();
    Set<String> eset = new HashSet<>();
    for (StructureDefinitionContextComponent ec : sd.getContext()) {
      switch (ec.getType()) {
      case ELEMENT:
        eset.add(ec.getExpression());
        tset.add(root(ec.getExpression()));
        break;
      case EXTENSION:
        break;
      case FHIRPATH:
        break;
      case NULL:
        break;
      default:
        break;
      }
    }
    for (String s : tset) {
      seeReference(resKey, 2, s);
    }
    return "Context: "+CommaSeparatedStringBuilder.join(",", eset)+"|Type:"+typeDetails(sd)+"|Mod:"+(ProfileUtilities.isModifierExtension(sd) ? "1" : "0");
  }

  private String typeDetails(StructureDefinition sd) throws SQLException {
    if (ProfileUtilities.isComplexExtension(sd)) {
      return "complex";
    } else {
      ElementDefinition ed = sd.getSnapshot().getElementByPath("Extension.value[x]");
      Set<String> tset = new HashSet<>();
      for (TypeRefComponent tr : ed.getType()) {
        tset.add(tr.getWorkingCode());
      }
      for (String s : tset) {
        seeReference(resKey, 3, s);
      }
      return CommaSeparatedStringBuilder.join(",", tset);
    }
  }

  private String root(String e) {
    return e.contains(".") ? e.substring(0, e.indexOf(".")) : e;
  }

  //  private String processStructureDefinition(int resKey, StructureDefinition sd) throws SQLException {
  //    Set<String> set = new HashSet<>();
  //    for (ElementDefinition ed : sd.getDifferential().getElement()) {
  //      for (TypeRefComponent tr : ed.getType()) {
  //        if (Utilities.isAbsoluteUrl(tr.getWorkingCode()) && !isCore(tr.getWorkingCode())) {
  //          set.add(seeReference(resKey, tr.getWorkingCode()));
  //        }
  //        for (CanonicalType c : tr.getProfile()) {
  //          if (!isCore(c.getValue())) {
  //            set.add(seeReference(resKey, c.asStringValue()));
  //          }
  //        }
  //        for (CanonicalType c : tr.getTargetProfile()) {
  //          if (!isCore(c.getValue())) {
  //            set.add(seeReference(resKey, c.asStringValue()));
  //          }
  //        }
  //      }
  //    }
  //    return CommaSeparatedStringBuilder.join(",", set);
  //  }

  private boolean isCore(String url) {
    return url.startsWith("http://hl7.org/fhir/StructureDefinition") || url.startsWith("http://hl7.org/fhir/ValueSet")
        || url.startsWith("http://hl7.org/fhir/CodeSystem")  || url.startsWith("http://hl7.org/fhir/SearchParameter");
  }

  private String processConceptMap(int resKey, ConceptMap cm, NpmPackage npm, Set<String> dependencies) throws SQLException {
    Set<String> set = new HashSet<>();
    if (cm.hasSourceScope()) {
      set.add(seeTxReference(cm.getSourceScope().primitiveValue(), npm));
      recordDependency(dependencies, resKey, cm.getSourceScope().primitiveValue());
    }
    if (cm.hasTargetScope()) {
      set.add(seeTxReference(cm.getTargetScope().primitiveValue(), npm));
      recordDependency(dependencies, resKey, cm.getTargetScope().primitiveValue());
    }
    for (ConceptMapGroupComponent g : cm.getGroup()) {
      set.add(seeTxReference(g.getSource(), npm));
      recordDependency(dependencies, resKey, g.getSource());
      set.add(seeTxReference(g.getTarget(), npm));
      recordDependency(dependencies, resKey, g.getTarget());
    }
    for (String s : set) {
      seeReference(resKey, 1, s);
    }
    return CommaSeparatedStringBuilder.join(",", set);
  }

  private String processValueSet(int resKey, ValueSet vs, NpmPackage npm, Set<String> dependencies) throws SQLException {
    Set<String> set = new HashSet<>();
    for (ConceptSetComponent inc : vs.getCompose().getInclude()) {
      for (CanonicalType c : inc.getValueSet()) {
        recordDependency(dependencies, resKey, c.getValueAsString());
        set.add(seeTxReference(c.getValue(), npm));
      }
      set.add(seeTxReference(inc.getSystem(), npm));
      recordDependency(dependencies, resKey, inc.getSystem());
    }
    if (vs.hasExpansion()) {
      for (ValueSetExpansionParameterComponent p : vs.getExpansion().getParameter()) {
        if (p.hasValue()) {
          recordDependency(dependencies, resKey, p.getValue().primitiveValue());
        }
      }
      checkVSEDependencies(resKey, dependencies, vs.getExpansion().getContains());
    }
    for (String s : set) {
      seeReference(resKey, 1, s);
    }
    return CommaSeparatedStringBuilder.join(",", set);
  }

  private void checkVSEDependencies(int resKey, Set<String> dependencies, List<ValueSetExpansionContainsComponent> list) throws SQLException {
    for (ValueSetExpansionContainsComponent c : list) {
      recordDependency(dependencies, resKey, c.getSystem());
      if (c.hasContains()) {
        checkVSEDependencies(resKey, dependencies, c.getContains());
      }
    }
  }

  private String seeTxReference(String system, NpmPackage npm) {
    if (!Utilities.noString(system)) {
      if (system.contains("http://snomed.info/sct") || system.contains("snomed") || system.contains("sct") ) {
        return "sct";
      } else if (system.startsWith("http://loinc.org")) {
        return  "loinc";
      } else if (system.contains("http://unitsofmeasure.org")) {
        return "ucum";
      } else if ("http://hl7.org/fhir/sid/ndc".equals(system)) {
        return "ndc";
      } else if ("http://hl7.org/fhir/sid/cvx".equals(system)) {
        return "cvx";
      } else if (system.contains("iso.org") || system.contains(":iso:")) {
        return "iso";
      } else if (system.contains("cms.gov")) {
        return "cms";
      } else if (system.contains("cdc.gov")) {
        return "cdc";
      } else if (system.contains(":ietf:") || system.contains(":iana:")) {
        return "ietf";
      } else if (system.contains("ihe.net") || system.contains(":ihe:")) {
        return "ihe";
      } else if (system.contains("icpc")) {
        return "icpc";
      } else if (system.contains("ncpdp")) {
        return "ncpdp";
      } else if (system.contains("x12.org")) {
        return "x12";
      } else if (system.contains("nucc")) {
        return "nucc";
      } else if (Utilities.existsInList(system, "http://hl7.org/fhir/sid/icd-9-cm", "http://hl7.org/fhir/sid/icd-10", "http://fhir.de/CodeSystem/dimdi/icd-10-gm", "http://hl7.org/fhir/sid/icd-10-nl 2.16.840.1.113883.6.3.2", "http://hl7.org/fhir/sid/icd-10-cm", "http://id.who.int/icd11/mms")) {
        return "icd";
      } else if (system.contains("urn:oid:")) {
        return "oid";
      } else if ("http://unitsofmeasure.org".equals(system)) {
        return "ucum";
      } else if ("http://dicom.nema.org/resources/ontology/DCM".equals(system) || system.contains("http://dicom.nema.org/medical")) {
        return "dcm";
      } else if ("http://www.ama-assn.org/go/cpt".equals(system)) {
        return "cpt";
      } else if ("http://www.nlm.nih.gov/research/umls/rxnorm".equals(system)) {
        return "rx";
      } else if (system.startsWith("http://cts.nlm.nih.gov")) {
        return "vsac";
      } else if (system.startsWith("http://terminology.hl7.org")) {
        return "tho";
      } else if (system.startsWith("http://www.whocc.no/atc")) {
        return "atc";
      } else if (system.startsWith("http://ncicb.nci.nih.gov/xml/owl")) {
        return "ncit";
      } else if (system.startsWith("http://hl7.org/fhir")) {
        return "fhir";
      } else if (system.startsWith("http://sequenceontology.org") || system.startsWith("http://www.ebi.ac.uk/ols/ontologies/gen")  || system.startsWith("http://human-phenotype-ontology.org") || 
          system.startsWith("http://purl.obolibrary.org/obo/sepio-clingen") ||  system.startsWith("http://www.genenames.org") ||  system.startsWith("http://varnomen.hgvs.org")) {
        return "gene";
      } else if (npm.canonical() != null && system.startsWith(npm.canonical())) {
        return "internal";
      } else if (system.contains("example.org")) {
        return "example";
      } else {
        // System.out.println("Uncategorised: "+system);
        return null;
      }
    } 
    return null;
  }

  private void defineTxSources(Statement stmt) throws SQLException {
    stmt.execute("insert into TxSource (Code, Display) values ('sct', 'SNOMED-CT')");
    stmt.execute("insert into TxSource (Code, Display) values ('loinc', 'LOINC')");
    stmt.execute("insert into TxSource (Code, Display) values ('ucum', 'UCUM')");
    stmt.execute("insert into TxSource (Code, Display) values ('ndc', 'NDC')");
    stmt.execute("insert into TxSource (Code, Display) values ('cvx', 'CVX')");
    stmt.execute("insert into TxSource (Code, Display) values ('iso', 'ISO Standard')");
    stmt.execute("insert into TxSource (Code, Display) values ('ietf', 'IETF')");
    stmt.execute("insert into TxSource (Code, Display) values ('ihe', 'IHE')");
    stmt.execute("insert into TxSource (Code, Display) values ('icpc', 'ICPC Variant')");
    stmt.execute("insert into TxSource (Code, Display) values ('ncpdp', 'NCPDP')");
    stmt.execute("insert into TxSource (Code, Display) values ('nucc', 'NUCC')");
    stmt.execute("insert into TxSource (Code, Display) values ('icd', 'ICD-X')");
    stmt.execute("insert into TxSource (Code, Display) values ('oid', 'OID-Based')");
    stmt.execute("insert into TxSource (Code, Display) values ('dcm', 'DICOM')");
    stmt.execute("insert into TxSource (Code, Display) values ('cpt', 'CPT')");
    stmt.execute("insert into TxSource (Code, Display) values ('rx', 'RxNorm')");
    stmt.execute("insert into TxSource (Code, Display) values ('tho', 'terminology.hl7.org')");
    stmt.execute("insert into TxSource (Code, Display) values ('fhir', 'hl7.org/fhir')");
    stmt.execute("insert into TxSource (Code, Display) values ('internal', 'Internal')");
    stmt.execute("insert into TxSource (Code, Display) values ('example', 'Example')");
    stmt.execute("insert into TxSource (Code, Display) values ('vsac', 'VSAC')");
    stmt.execute("insert into TxSource (Code, Display) values ('act', 'ATC')");
    stmt.execute("insert into TxSource (Code, Display) values ('ncit', 'NCI-Thesaurus')");
    stmt.execute("insert into TxSource (Code, Display) values ('x12', 'X12')");
    stmt.execute("insert into TxSource (Code, Display) values ('cms', 'CMS (USA)')");
    stmt.execute("insert into TxSource (Code, Display) values ('cdc', 'CDC (USA)')");
    stmt.execute("insert into TxSource (Code, Display) values ('gene', 'Sequence Codes')");
  }

  private void seeReference(int resKey, int mode, String code) throws SQLException {
    if (code != null) {
      psqlCat.setInt(1, resKey);
      psqlCat.setInt(2, mode);
      psqlCat.setString(3, code);
      psqlCat.execute();
    }
  }

  private int processCodes(List<ConceptDefinitionComponent> concepts) throws SQLException {
    int c = concepts.size();
    for (ConceptDefinitionComponent concept : concepts) {
      psqlCI.setInt(1, resKey);
      psqlCI.setString(2, concept.getCode());
      psqlCI.setString(3, concept.getDisplay());
      psqlCI.setString(4, concept.getDefinition());
      psqlCI.execute();
      c = c + processCodes(concept.getConcept());
    }    
    return c;
  }

  public static byte[] gzip(byte[] bytes) throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();

    GzipParameters gp = new GzipParameters();
    gp.setCompressionLevel(Deflater.BEST_COMPRESSION);
    GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bOut, gp);
    gzip.write(bytes);
    gzip.flush();
    gzip.close();
    return bOut.toByteArray();
  }

  public static byte[] unGzip(byte[] bytes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try{
      IOUtils.copy(new GZIPInputStream(new ByteArrayInputStream(bytes)), out);
    } catch(IOException e){
      throw new RuntimeException(e);
    }
    return out.toByteArray();
  }

  private int hasVersion(String fhirVersionList, String ver) {
    return fhirVersionList.startsWith(ver) || fhirVersionList.contains(","+ver) ? 1 : 0;
  }

  private boolean isCoreDefinition(String pid) {
    return Utilities.startsWithInList(pid, "hl7.fhir.r2", "hl7.fhir.r2b", "hl7.fhir.r3", "hl7.fhir.r4", "hl7.fhir.r4b", "hl7.fhir.r5", "hl7.fhir.r6", "hl7.fhir.xver");
  }

  private Resource loadResource(String pid, String parseVersion, String type, String id, byte[] source, SpecMapManager smm) {
    try {
      if (parseVersion.equals("current")) {
        return null;
      }
      if (VersionUtilities.isR3Ver(parseVersion)) {
        org.hl7.fhir.dstu3.model.Resource res;
        res = new org.hl7.fhir.dstu3.formats.JsonParser(true).parse(source);
        scanForExtensionUsage(pid, res, smm.getPath(res.fhirType(), res.getIdBase()));
        scanForDocuments(res);
        return VersionConvertorFactory_30_50.convertResource(res);
      } else if (VersionUtilities.isR4Ver(parseVersion)) {
        org.hl7.fhir.r4.model.Resource res;
        res = new org.hl7.fhir.r4.formats.JsonParser(true, true).parse(source);
        scanForExtensionUsage(pid, res, smm.getPath(res.fhirType(), res.getIdBase()));
        scanForDocuments(res);
        return VersionConvertorFactory_40_50.convertResource(res);
//      } else if (VersionUtilities.isR2BVer(parseVersion)) {
//        org.hl7.fhir.dstu2016may.model.Resource res;
//        res = new org.hl7.fhir.dstu2016may.formats.JsonParser(true).parse(source);
//        scanForExtensionUsage(res, Utilities.pathURL(smm.getBase(), smm.getPath(res.fhirType(), res.getId())));
//        return VersionConvertorFactory_14_50.convertResource(res);
//      } else if (VersionUtilities.isR2Ver(parseVersion)) {
//        org.hl7.fhir.dstu2.model.Resource res;
//        res = new org.hl7.fhir.dstu2.formats.JsonParser(true).parse(source);
//        scanForExtensionUsage(res, Utilities.pathURL(smm.getBase(), smm.getPath(res.fhirType(), res.getId())));
//
//        BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
//        return VersionConvertorFactory_10_50.convertResource(res, advisor);
      } else if (VersionUtilities.isR4BVer(parseVersion)) {
        org.hl7.fhir.r4b.model.Resource res;
        res = new org.hl7.fhir.r4b.formats.JsonParser(true).parse(source);
        scanForExtensionUsage(pid, res, smm.getPath(res.fhirType(), res.getIdBase()));
        scanForDocuments(res);
        return VersionConvertorFactory_43_50.convertResource(res);
      } else if (VersionUtilities.isR5Plus(parseVersion)) {
        Resource res = new JsonParser(true, true).parse(source);
        scanForExtensionUsage(pid, res, smm.getPath(res.fhirType(), res.getIdBase()));
        scanForDocuments(res);
        return res;
      } else if (Utilities.existsInList(parseVersion, "4.6.0", "3.5.0", "1.8.0")) {
        return null;
      } else {
        throw new Exception("Unsupported version "+parseVersion);
      }    

    } catch (Exception e) {
      System.out.println("Error loading "+type+"/"+id+" from "+pid+"("+parseVersion+"):" +e.getMessage());
//      e.printStackTrace();
      return null;
    }
  }

  private void scanForDocuments(org.hl7.fhir.dstu3.model.Resource res) throws SQLException {
    org.hl7.fhir.dstu3.model.Composition cmp = null;
    List<org.hl7.fhir.dstu3.model.UriType> p = new ArrayList<>();
    if (res instanceof org.hl7.fhir.dstu3.model.Composition) {
      cmp = (org.hl7.fhir.dstu3.model.Composition) res;
      p.addAll(cmp.getMeta().getProfile());
    } else if (res instanceof org.hl7.fhir.dstu3.model.Bundle) {
      org.hl7.fhir.dstu3.model.Bundle b = (org.hl7.fhir.dstu3.model.Bundle) res;
      org.hl7.fhir.dstu3.model.Resource r = b.getEntryFirstRep().getResource();
      if (r != null && r instanceof Composition) {
        cmp = (org.hl7.fhir.dstu3.model.Composition) r;
        p.addAll(b.getMeta().getProfile());
        p.addAll(cmp.getMeta().getProfile());
      }
    }
    if (cmp != null) {
      docKey++;
      sqlAddDocument.setInt(1, docKey);
      sqlAddDocument.setInt(2, resKey);
      sqlAddDocument.setInt(3, 1);
      sqlAddDocument.execute();
      for (var pi : p) {
        sqlAddDocumentProfile.setInt(1, docKey);
        sqlAddDocumentProfile.setString(2, pi.primitiveValue());
        sqlAddDocumentProfile.execute();
      }
      for (var c : cmp.getType().getCoding()) {
        sqlAddDocumentCode.setInt(1, docKey);
        sqlAddDocumentCode.setString(2, c.getSystem());
        sqlAddDocumentCode.setString(3, c.getVersion());
        sqlAddDocumentCode.setString(4, c.getCode());
        sqlAddDocumentCode.setString(5, c.getDisplay());
        sqlAddDocumentCode.execute();
      }
    }
  }


  private void scanForDocuments(org.hl7.fhir.r4.model.Resource res) throws SQLException {
    org.hl7.fhir.r4.model.Composition cmp = null;
    List<org.hl7.fhir.r4.model.CanonicalType> p = new ArrayList<>();
    if (res instanceof org.hl7.fhir.r4.model.Composition) {
      cmp = (org.hl7.fhir.r4.model.Composition) res;
      p.addAll(cmp.getMeta().getProfile());
    } else if (res instanceof org.hl7.fhir.r4.model.Bundle) {
      org.hl7.fhir.r4.model.Bundle b = (org.hl7.fhir.r4.model.Bundle) res;
      org.hl7.fhir.r4.model.Resource r = b.getEntryFirstRep().getResource();
      if (r != null && r instanceof org.hl7.fhir.r4.model.Composition) {
        cmp = (org.hl7.fhir.r4.model.Composition) r;
        p.addAll(b.getMeta().getProfile());
        p.addAll(cmp.getMeta().getProfile());
      }
    }
    if (cmp != null) {
      docKey++;
      sqlAddDocument.setInt(1, docKey);
      sqlAddDocument.setInt(2, resKey);
      sqlAddDocument.setInt(3, 1);
      sqlAddDocument.execute();
      for (var pi : p) {
        sqlAddDocumentProfile.setInt(1, docKey);
        sqlAddDocumentProfile.setString(2, pi.primitiveValue());
        sqlAddDocumentProfile.execute();
      }
      for (var c : cmp.getType().getCoding()) {
        sqlAddDocumentCode.setInt(1, docKey);
        sqlAddDocumentCode.setString(2, c.getSystem());
        sqlAddDocumentCode.setString(3, c.getVersion());
        sqlAddDocumentCode.setString(4, c.getCode());
        sqlAddDocumentCode.setString(5, c.getDisplay());
        sqlAddDocumentCode.execute();
      }
    }
  }


  private void scanForDocuments(org.hl7.fhir.r4b.model.Resource res) throws SQLException {
    org.hl7.fhir.r4b.model.Composition cmp = null;
    List<org.hl7.fhir.r4b.model.CanonicalType> p = new ArrayList<>();
    if (res instanceof org.hl7.fhir.r4b.model.Composition) {
      cmp = (org.hl7.fhir.r4b.model.Composition) res;
      p.addAll(cmp.getMeta().getProfile());
    } else if (res instanceof org.hl7.fhir.r4b.model.Bundle) {
      org.hl7.fhir.r4b.model.Bundle b = (org.hl7.fhir.r4b.model.Bundle) res;
      org.hl7.fhir.r4b.model.Resource r = b.getEntryFirstRep().getResource();
      if (r != null && r instanceof org.hl7.fhir.r4b.model.Composition) {
        cmp = (org.hl7.fhir.r4b.model.Composition) r;
        p.addAll(b.getMeta().getProfile());
        p.addAll(cmp.getMeta().getProfile());
      }
    }
    if (cmp != null) {
      docKey++;
      sqlAddDocument.setInt(1, docKey);
      sqlAddDocument.setInt(2, resKey);
      sqlAddDocument.setInt(3, 1);
      sqlAddDocument.execute();
      for (var pi : p) {
        sqlAddDocumentProfile.setInt(1, docKey);
        sqlAddDocumentProfile.setString(2, pi.primitiveValue());
        sqlAddDocumentProfile.execute();
      }
      for (var c : cmp.getType().getCoding()) {
        sqlAddDocumentCode.setInt(1, docKey);
        sqlAddDocumentCode.setString(2, c.getSystem());
        sqlAddDocumentCode.setString(3, c.getVersion());
        sqlAddDocumentCode.setString(4, c.getCode());
        sqlAddDocumentCode.setString(5, c.getDisplay());
        sqlAddDocumentCode.execute();
      }
    }
  }


  private void scanForDocuments(org.hl7.fhir.r5.model.Resource res) throws SQLException {
    org.hl7.fhir.r5.model.Composition cmp = null;
    List<org.hl7.fhir.r5.model.CanonicalType> p = new ArrayList<>();
    if (res instanceof org.hl7.fhir.r5.model.Composition) {
      cmp = (org.hl7.fhir.r5.model.Composition) res;
      p.addAll(cmp.getMeta().getProfile());
    } else if (res instanceof org.hl7.fhir.r5.model.Bundle) {
      org.hl7.fhir.r5.model.Bundle b = (org.hl7.fhir.r5.model.Bundle) res;
      org.hl7.fhir.r5.model.Resource r = b.getEntryFirstRep().getResource();
      if (r != null && r instanceof org.hl7.fhir.r5.model.Composition) {
        cmp = (org.hl7.fhir.r5.model.Composition) r;
        p.addAll(b.getMeta().getProfile());
        p.addAll(cmp.getMeta().getProfile());
      }
    }
    if (cmp != null) {
      docKey++;
      sqlAddDocument.setInt(1, docKey);
      sqlAddDocument.setInt(2, resKey);
      sqlAddDocument.setInt(3, 1);
      sqlAddDocument.execute();
      for (var pi : p) {
        sqlAddDocumentProfile.setInt(1, docKey);
        sqlAddDocumentProfile.setString(2, pi.primitiveValue());
        sqlAddDocumentProfile.execute();
      }
      for (var c : cmp.getType().getCoding()) {
        sqlAddDocumentCode.setInt(1, docKey);
        sqlAddDocumentCode.setString(2, c.getSystem());
        sqlAddDocumentCode.setString(3, c.getVersion());
        sqlAddDocumentCode.setString(4, c.getCode());
        sqlAddDocumentCode.setString(5, c.getDisplay());
        sqlAddDocumentCode.execute();
      }
    }
  }



  private void scanForExtensionUsage(String pid,org.hl7.fhir.dstu3.model.Resource res, String path) throws SQLException {
    if (path != null) {
      int key = getExtnUsageKey(pid, res.fhirType(), res.getId(), path, 3);
      new org.hl7.fhir.dstu3.utils.ElementVisitor(new XIGExtensionUsageProcessor.ExtensionVisitorR3(key, extensionUrls, psqlExtnUrl, psqlExtnUse)).visit(null, res);
    }
  }

  private void scanForExtensionUsage(String pid,org.hl7.fhir.r4.model.Resource res, String path) throws SQLException {
    if (path != null) {
      int key = getExtnUsageKey(pid, res.fhirType(), res.getId(), path, 4);
      new org.hl7.fhir.r4.utils.ElementVisitor(new XIGExtensionUsageProcessor.ExtensionVisitorR4(key, extensionUrls, psqlExtnUrl, psqlExtnUse)).visit(null, res);
    }
  }

  private void scanForExtensionUsage(String pid,org.hl7.fhir.r4b.model.Resource res, String path) throws SQLException {
    if (path != null) {
      int key = getExtnUsageKey(pid, res.fhirType(), res.getId(), path, 5);
      new org.hl7.fhir.r4b.utils.ElementVisitor(new XIGExtensionUsageProcessor.ExtensionVisitorR4B(key, extensionUrls, psqlExtnUrl, psqlExtnUse)).visit(null, res);
    }
  }

  private void scanForExtensionUsage(String pid,org.hl7.fhir.r5.model.Resource res, String path) throws SQLException {
    if (path != null) {
      int key = getExtnUsageKey(pid, res.fhirType(), res.getId(), path, 6);
      new org.hl7.fhir.r5.utils.ElementVisitor(new XIGExtensionUsageProcessor.ExtensionVisitorR5(key, extensionUrls, psqlExtnUrl, psqlExtnUse)).visit(null, res);
    }
  }

  private int getExtnUsageKey(String pid,String type, String id, String path, int ver) throws SQLException {
    int key;
    if (extensionUsers.containsKey(path)) {
      key = extensionUsers.get(path);
    } else {
      key = extensionUsers.size()+1;
      extensionUsers.put(path, key);
      psqlExtnUser.setInt(1, key);
      psqlExtnUser.setString(2, path);
      psqlExtnUser.setString(3, pid+":"+type+"/"+id);
      psqlExtnUser.setInt(4, ver);
      psqlExtnUser.execute();
    }
    return key;
  }

  private String getAuth(String pid, CanonicalResource cr) {
    if (pid.contains("#")) {
      pid = pid.substring(0, pid.indexOf("#"));
    }
    if (pid.startsWith("hl7.") || pid.startsWith("hl7se.") || pid.startsWith("fhir.") || pid.startsWith("ch.fhir.")) {
      return seeAuth("hl7");
    }
    if (pid.startsWith("ihe.")) {
      return seeAuth("ihe");
    }
    if (pid.startsWith("ihe-")) {
      return seeAuth("ihe");
    }
    if (pid.startsWith("au.digital")) {
      return seeAuth("national");
    }
    if (pid.startsWith("ndhm.in")) {
      return seeAuth("national");
    }
    if (pid.startsWith("tw.gov")) {
      return seeAuth("national");
    }
    if (cr != null) {
      String p = cr.getPublisher();
      if (p != null) {
        if (p.contains("Te Whatu Ora")) {
          return "national";
        }
        if (p.contains("HL7")) {
          return "hl7";
        }
        if (p.contains("WHO")) {
          return "who";
        }
        switch (p) {
        case "Argonaut": return "national";
        case "Te Whatu Ora": return "national";
        case "ANS": return "national";
        case "Canada Health Infoway": return "national";
        case "Carequality": return "carequality";
        case "Israeli Ministry of Health" : return "national";
        default: 
          //          possibleRealms.add(pid+" : "+p);
          return null;
        }
      }
    }
    //    possibleRealms.add(pid);
    return null;
  }

  private String seeAuth(String a) {
    authorities.add(a);
    return a;
  }

  private String getRealm(String pid, CanonicalResource cr) {
    if (pid.contains("#")) {
      pid = pid.substring(0, pid.indexOf("#"));
    }
    if (pid.startsWith("hl7.fhir.")) {
      String s = pid.split("\\.")[2];
      if (Utilities.existsInList(s,  "core", "pubpack")) {
        return "uv";
      } else {
        return seeRealm(s);
      }
    } 
    if (pid.startsWith("hl7.cda.")) {
      return seeRealm(pid.split("\\.")[2]);
    }
    if (pid.startsWith("hl7.fhirpath") || pid.startsWith("hl7.terminology") ) {
      return seeRealm("uv");
    }
    if (cr != null && cr.hasJurisdiction()) {
      String j = cr.getJurisdictionFirstRep().getCodingFirstRep().getCode();
      if (j != null) {
        switch (j) {
        case "001" : return seeRealm("uv");
        case "150" : return seeRealm("eu");
        case "840" : return seeRealm("us");
        case "AU" :  return seeRealm("au");
        case "NZ" :  return seeRealm("nz");
        case "BE" :  return seeRealm("be");
        case "EE" :  return seeRealm("ee");
        case "CH" :  return seeRealm("ch");
        case "DK" :  return seeRealm("dk");
        case "IL" :  return seeRealm("il");
        case "CK" :  return seeRealm("ck");
        case "CA" :  return seeRealm("ca");
        case "GB" :  return seeRealm("uk");
        case "CHE" :  return seeRealm("ch");
        case "US" :  return seeRealm("us");
        case "SE" :  return seeRealm("se");
        case "BR" :  return seeRealm("br");
        case "NL" :  return seeRealm("nl");
        case "DE" :  return seeRealm("de");
        case "NO" :  return seeRealm("no");
        case "IN" :  return seeRealm("in");
        default: 
          //          possibleAuthorities.add(j+" : "+pid);
          return null;
        }
      }
    }
    if (pid.startsWith("fhir.") || pid.startsWith("us.")) {
      return seeRealm("us");
    }
    if (pid.startsWith("ch.fhir.")) {
      return seeRealm("ch");
    }
    if (pid.startsWith("swiss.")) {
      return seeRealm("ch");
    }
    if (pid.startsWith("who.")) {
      return seeRealm("uv");
    }
    if (pid.startsWith("au.")) {
      return seeRealm("au");
    }
    if (pid.contains(".de#")) {
      return seeRealm("de");
    }
    if (pid.startsWith("ehi.")) {
      return seeRealm("us");
    }
    if (pid.startsWith("hl7.eu")) {
      return seeRealm("eu");
    }
    if (pid.startsWith("hl7se.")) {
      return seeRealm("se");
    }
    if (pid.startsWith("ihe.")) {
      return seeRealm("uv");
    }
    if (pid.startsWith("tw.")) {
      return seeRealm("tw");
    }
    if (pid.contains(".dk.")) {
      return seeRealm("dk");
    }
    if (pid.contains(".sl.")) {
      return seeRealm("sl");
    }
    if (pid.contains(".nl.")) {
      return seeRealm("nl");
    }
    if (pid.contains(".fr.")) {
      return seeRealm("fr");
    }
    if (pid.startsWith("cinc.")) {
      return seeRealm("nz");
    }
    if (pid.contains(".nz.")) {
      return seeRealm("nz");
    }
    if (pid.startsWith("jp-")) {
      return seeRealm("jp");
    }
    //    possibleAuthorities.add(pid);
    return null;
  }

  private String seeRealm(String r) {
    if ("mi".equals(r)) {
      return null;
    }
    realms.add(r);
    return r;
  }

  @Override
  public void finishPackage(PackageContext context) throws FHIRException, IOException, EOperationOutcome {

  }

  @Override
  public void alreadyVisited(String pid) throws FHIRException, IOException, EOperationOutcome {
    try {
      pck++;
      Statement stmt = con.createStatement();
      stmt.execute("Update Packages set Published = 1 where ID = '"+pid+"'");
    } catch (SQLException e) {
      throw new FHIRException(e);
    } 

  }

  public Set<String> getResourceTypes() {
    return resourceTypes;
  }
}
