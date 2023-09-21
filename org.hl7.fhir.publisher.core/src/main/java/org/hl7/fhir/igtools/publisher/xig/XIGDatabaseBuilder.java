package org.hl7.fhir.igtools.publisher.xig;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_10_50;
import org.hl7.fhir.convertors.analytics.PackageVisitor.IPackageVisitorProcessor;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_10_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_14_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_30_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.IGR2ConvertorAdvisor5;
import org.hl7.fhir.igtools.publisher.SpecMapManager;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.PackageInformation;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ConceptSetComponent;
import org.hl7.fhir.r5.utils.EOperationOutcome;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.hl7.fhir.utilities.npm.PackageHacker;

public class XIGDatabaseBuilder implements IPackageVisitorProcessor {


  private Connection con;

  private PreparedStatement psqlP;
  private int pckKey;
  private PreparedStatement psqlR;
  private int resKey;
  private PreparedStatement psqlC;
  private PreparedStatement psqlRI;
  private PreparedStatement psqlCI;
  private Set<String> vurls = new HashSet<>();
  private int lastMDKey;
  
  private Map<String, SpecMapManager> smmList = new HashMap<>();

  public XIGDatabaseBuilder(String dest, String date) throws IOException {
    super();
    try {
      con = connect(dest, date);

      psqlP = con.prepareStatement("Insert into Packages (PackageKey, PID, Id, Date, Title, Canonical, Web, Version, R2, R2B, R3, R4, R4B, R5, R6, Realm, Auth, Package) Values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      psqlR = con.prepareStatement("Insert into Resources (ResourceKey, PackageKey, ResourceType, ResourceTypeR5, Id, R2, R2B, R3, R4, R4B, R5, R6, Web, Url, Version, Status, Date, Name, Title, Experimental, Realm, Description, Purpose, Copyright, CopyrightLabel, Kind, Type, Supplements, Content) Values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      psqlC = con.prepareStatement("Insert into Contents (ResourceKey, Json, JsonR5) Values (?, ?, ?)");
      psqlRI = con.prepareStatement("Insert into ResourceFTS (ResourceKey, Name, Title, Description, Narrative) Values (?, ?, ?, ?, ?)");
      psqlCI = con.prepareStatement("Insert into CodeSystemFTS (ResourceKey, Code, Display, Definition) Values (?, ?, ?, ?)");
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  private Connection connect(String filename, String date) throws IOException, SQLException {
    new File(filename).delete();
    Connection con = DriverManager.getConnection("jdbc:sqlite:"+filename); 
    makeMetadataTable(con);
    makePackageTable(con);
    makeResourcesTable(con);
    makeContentsTable(con);
    makeResourceIndex(con);
    makeCodeIndex(con);
    PreparedStatement psql = con.prepareStatement("Insert into Metadata (key, name, value) values (?, ?, ?)");
    psql.setInt(1, ++lastMDKey);
    psql.setString(2, "date");
    psql.setString(3, date);
    psql.executeUpdate();
    return con;    
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
        "ResourceTypeR5  nvarchar NOT NULL,\r\n"+
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
        "Description     nvarchar NULL,\r\n"+
        "Purpose         nvarchar NULL,\r\n"+
        "Copyright       nvarchar NULL,\r\n"+
        "CopyrightLabel  nvarchar NULL,\r\n"+
        "Content         nvarchar NULL,\r\n"+
        "Type            nvarchar NULL,\r\n"+
        "Supplements     nvarchar NULL,\r\n"+
        "Kind            nvarchar NULL,\r\n"+
        "PRIMARY KEY (ResourceKey))\r\n");
  }


  private void makeContentsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Contents (\r\n"+
        "ResourceKey     integer NOT NULL,\r\n"+
        "Json            BLOB NOT NULL,\r\n"+
        "JsonR5          BLOB NULL,\r\n"+
        "PRIMARY KEY (ResourceKey))\r\n");
    
  }
  
  public void finish() throws IOException {
    try {
      con.close();
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public void processResource(String pid, NpmPackage npm, String version, String type, String id, byte[] content) throws FHIRException, IOException, EOperationOutcome {
    if (!isCoreDefinition(pid)) {

      try {
        SpecMapManager smm = smmList.get(pid);
        if (smm == null) {
          smm = npm.hasFile("other", "spec.internals") ?  new SpecMapManager( TextFile.streamToBytes(npm.load("other", "spec.internals")), npm.fhirVersion()) : SpecMapManager.createSpecialPackage(npm);
          pckKey++;
          smm.setName(npm.name());
          smm.setBase(npm.canonical());
          smm.setBase2(PackageHacker.fixPackageUrl(npm.url()));
          smm.setKey(pckKey);
          smmList.put(pid, smm);

          psqlP.setInt(1, pckKey);
          psqlP.setString(2, pid);
          psqlP.setString(3, npm.name());
          psqlP.setString(4, npm.date());
          psqlP.setString(5, npm.title());
          psqlP.setString(6, npm.canonical()); 
          psqlP.setString(7, npm.getWebLocation());
          psqlP.setString(8, npm.version()); 
          psqlP.setInt(9, hasVersion(npm.fhirVersionList(), "1.0"));
          psqlP.setInt(10, hasVersion(npm.fhirVersionList(), "1.4"));
          psqlP.setInt(11, hasVersion(npm.fhirVersionList(), "3.0"));
          psqlP.setInt(12, hasVersion(npm.fhirVersionList(), "4.0"));
          psqlP.setInt(13, hasVersion(npm.fhirVersionList(), "4.3"));
          psqlP.setInt(14, hasVersion(npm.fhirVersionList(), "5.0"));
          psqlP.setInt(15, hasVersion(npm.fhirVersionList(), "6.0"));
          psqlP.setString(16, getRealm(pid));
          psqlP.setString(17, getAuth(pid));
          psqlP.setBytes(18, org.hl7.fhir.utilities.json.parser.JsonParser.composeBytes(npm.getNpm()));
          psqlP.execute();
        }

        Resource r = loadResource(pid, version, type, id, content);
        if (r != null && r instanceof CanonicalResource) {
          CanonicalResource cr = (CanonicalResource) r;  
          if (!vurls.contains(cr.getUrl())) {
            vurls.add(cr.getUrl());
            JsonObject j = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(content);
            String narrative = cr.getText().getDiv().allText();
            cr.setText(null);
            resKey++;

            psqlR.setInt(1, resKey);
            psqlR.setInt(2, pckKey);
            psqlR.setString(3, type);
            psqlR.setString(4, r.fhirType());
            psqlR.setString(5, r.hasId() ? r.getId() : id.replace(".json", ""));
            psqlR.setInt(6, hasVersion(npm.fhirVersionList(), "1.0"));
            psqlR.setInt(7, hasVersion(npm.fhirVersionList(), "1.4"));
            psqlR.setInt(8, hasVersion(npm.fhirVersionList(), "3.0"));
            psqlR.setInt(9, hasVersion(npm.fhirVersionList(), "4.0"));
            psqlR.setInt(10, hasVersion(npm.fhirVersionList(), "4.3"));
            psqlR.setInt(11, hasVersion(npm.fhirVersionList(), "5.0"));
            psqlR.setInt(12, hasVersion(npm.fhirVersionList(), "6.0"));
            psqlR.setString(13, Utilities.pathURL(smm.getBase(), smm.getPath(cr.getUrl(), null, cr.fhirType(), cr.getIdBase())));
            psqlR.setString(14, cr.getUrl());
            psqlR.setString(15, cr.getVersion());
            psqlR.setString(16, cr.getStatus().toCode());
            psqlR.setString(17, cr.getDateElement().primitiveValue());
            psqlR.setString(18, cr.getName());
            psqlR.setString(19, cr.getTitle());
            psqlR.setBoolean(20, cr.getExperimental());
            psqlR.setString(21, getRealm(pid));
            psqlR.setString(22, cr.getDescription());
            psqlR.setString(23, cr.getPurpose());
            psqlR.setString(24, cr.getCopyright());
            psqlR.setString(25, cr.getCopyrightLabel()); 
            psqlR.setString(26, j.asString("kind"));
            psqlR.setString(27, j.asString("type"));        
            psqlR.setString(28, j.asString("supplements"));        
            psqlR.setString(29, j.asString("content"));        
            psqlR.execute();

            psqlC.setInt(1, resKey);
            psqlC.setBytes(2, gzip(content));
            psqlC.setBytes(3, gzip(new JsonParser().composeBytes(cr)));
            psqlC.execute();

            psqlRI.setInt(1, resKey);
            psqlRI.setString(2, cr.getName());
            psqlRI.setString(3, cr.getTitle());
            psqlRI.setString(4, cr.getDescription());
            psqlRI.setString(5, narrative);
            psqlRI.execute();

            if (cr instanceof CodeSystem) {
              processCodes(((CodeSystem) cr).getConcept());
            }
          }       
        }

      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private void processCodes(List<ConceptDefinitionComponent> concepts) throws SQLException {
    for (ConceptDefinitionComponent concept : concepts) {
      psqlCI.setInt(1, resKey);
      psqlCI.setString(2, concept.getCode());
      psqlCI.setString(3, concept.getDisplay());
      psqlCI.setString(4, concept.getDefinition());
      psqlCI.execute();
      processCodes(concept.getConcept());
    }    
  }

  private byte[] gzip(byte[] bytes) throws IOException {
    ByteArrayOutputStream bOut = new ByteArrayOutputStream();
    
    GzipParameters gp = new GzipParameters();
    gp.setCompressionLevel(Deflater.BEST_COMPRESSION);
    GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bOut, gp);
    gzip.write(bytes);
    gzip.flush();
    gzip.close();
    return bOut.toByteArray();
  }

  private int hasVersion(String fhirVersionList, String ver) {
    return fhirVersionList.startsWith(ver) || fhirVersionList.contains(","+ver) ? 1 : 0;
  }

  private boolean isCoreDefinition(String pid) {
    return Utilities.startsWithInList(pid, "hl7.fhir.r2", "hl7.fhir.r2b", "hl7.fhir.r3", "hl7.fhir.r4", "hl7.fhir.r4b", "hl7.fhir.r5", "hl7.fhir.r6", "hl7.fhir.xver");
  }

  private Resource loadResource(String pid, String parseVersion, String type, String id, byte[] source) {
    try {
      if (parseVersion.equals("current")) {
        return null;
      }
      if (VersionUtilities.isR3Ver(parseVersion)) {
        org.hl7.fhir.dstu3.model.Resource res;
        res = new org.hl7.fhir.dstu3.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_30_50.convertResource(res);
      } else if (VersionUtilities.isR4Ver(parseVersion)) {
        org.hl7.fhir.r4.model.Resource res;
        res = new org.hl7.fhir.r4.formats.JsonParser(true, true).parse(source);
        return VersionConvertorFactory_40_50.convertResource(res);
      } else if (VersionUtilities.isR2BVer(parseVersion)) {
        org.hl7.fhir.dstu2016may.model.Resource res;
        res = new org.hl7.fhir.dstu2016may.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_14_50.convertResource(res);
      } else if (VersionUtilities.isR2Ver(parseVersion)) {
        org.hl7.fhir.dstu2.model.Resource res;
        res = new org.hl7.fhir.dstu2.formats.JsonParser(true).parse(source);

        BaseAdvisor_10_50 advisor = new IGR2ConvertorAdvisor5();
        return VersionConvertorFactory_10_50.convertResource(res, advisor);
      } else if (VersionUtilities.isR4BVer(parseVersion)) {
        org.hl7.fhir.r4b.model.Resource res;
        res = new org.hl7.fhir.r4b.formats.JsonParser(true).parse(source);
        return VersionConvertorFactory_43_50.convertResource(res);
      } else if (VersionUtilities.isR5Plus(parseVersion)) {
        return new JsonParser(true, true).parse(source);
      } else if (Utilities.existsInList(parseVersion, "4.6.0", "3.5.0", "1.8.0")) {
        return null;
      } else {
        throw new Exception("Unsupported version "+parseVersion);
      }    

    } catch (Exception e) {
      System.out.println("Error loading "+type+"/"+id+" from "+pid+"("+parseVersion+"):" +e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  private String getAuth(String pid) {
    if (pid.startsWith("hl7.") || pid.startsWith("fhir.") || pid.startsWith("ch.fhir.")) {
      return "hl7";
    }
    if (pid.startsWith("ihe.")) {
      return "ihe";
    }
    return null;
  }

  private String getRealm(String pid) {
    if (pid.startsWith("hl7.fhir.")) {
      return pid.split("\\.")[2];
    }
    if (pid.startsWith("fhir.") || pid.startsWith("us.")) {
      return "us";
    }
    if (pid.startsWith("ch.fhir.")) {
      return "ch";
    }

    return null;
  }
}
