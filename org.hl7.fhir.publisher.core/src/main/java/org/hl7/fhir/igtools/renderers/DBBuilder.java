package org.hl7.fhir.igtools.renderers;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.igtools.publisher.FetchedFile;
import org.hl7.fhir.igtools.publisher.FetchedResource;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.extensions.ExtensionUtilities;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.r5.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r5.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r5.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.renderers.DataRenderer;
import org.hl7.fhir.r5.renderers.Renderer.RenderingStatus;
import org.hl7.fhir.r5.renderers.utils.RenderingContext;
import org.hl7.fhir.r5.renderers.utils.ResourceWrapper;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.r5.utils.UserDataNames;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.json.model.JsonArray;
import org.hl7.fhir.utilities.json.model.JsonBoolean;
import org.hl7.fhir.utilities.json.model.JsonNull;
import org.hl7.fhir.utilities.json.model.JsonNumber;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.json.model.JsonString;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class DBBuilder {

  public enum SQLControlColumnType { 
    Auto, Link, Text, Markdown, Url, Coding, Canonical, Resource;

    public static SQLControlColumnType fromCode(String type) {
      if (Utilities.noString(type)) {
        return Auto;
      }
      switch (type) {
      case "auto" : return Auto;
      case "link" : return Link;
      case "text" : return Text;
      case "markdown" : return Markdown;
      case "url" : return Url;
      case "coding" : return Coding;
      case "canonical" : return Canonical; 
      case "resource" : return Resource; 
      }
      throw new FHIRException("Unknown column type '"+type+"'");

    } 
  }
  
  public class SQLControlColumn {
    private SQLControlColumnType type;
    private String title;
    private String source;
    private int sourceIndex;
    private String target;
    private int targetIndex;
    private String system;
    private int systemIndex;
    private String display;
    private int displayIndex;
    private String version;
    private int versionIndex;
    
    public SQLControlColumnType getType() {
      return type;
    }
    public void setType(SQLControlColumnType type) {
      this.type = type;
    }
    public String getTitle() {
      return title;
    }
    public void setTitle(String title) {
      this.title = title;
    }
    public String getSource() {
      return source;
    }
    public void setSource(String source) {
      this.source = source;
    }
    public int getSourceIndex() {
      return sourceIndex;
    }
    public void setSourceIndex(int sourceIndex) {
      this.sourceIndex = sourceIndex;
    }
    public String getTarget() {
      return target;
    }
    public void setTarget(String target) {
      this.target = target;
    }
    public int getTargetIndex() {
      return targetIndex;
    }
    public void setTargetIndex(int targetIndex) {
      this.targetIndex = targetIndex;
    }
    public String getSystem() {
      return system;
    }
    public void setSystem(String system) {
      this.system = system;
    }
    public int getSystemIndex() {
      return systemIndex;
    }
    public void setSystemIndex(int systemIndex) {
      this.systemIndex = systemIndex;
    }
    public String getDisplay() {
      return display;
    }
    public void setDisplay(String display) {
      this.display = display;
    }
    public int getDisplayIndex() {
      return displayIndex;
    }
    public void setDisplayIndex(int displayIndex) {
      this.displayIndex = displayIndex;
    }
    public String getVersion() {
      return version;
    }
    public void setVersion(String version) {
      this.version = version;
    }
    public int getVersionIndex() {
      return versionIndex;
    }
    public void setVersionIndex(int versionIndex) {
      this.versionIndex = versionIndex;
    }
    
  }

  public class SQLControl {
    private String query;
    private String clss;
    private boolean titles;
    private List<SQLControlColumn> columns = new ArrayList<>();
    public String getQuery() {
      return query;
    }
    public void setQuery(String query) {
      this.query = query;
    }
    public String getClss() {
      return clss;
    }
    public void setClss(String clss) {
      this.clss = clss;
    }
    public boolean isTitles() {
      return titles;
    }
    public void setTitles(boolean titles) {
      this.titles = titles;
    }
    public List<SQLControlColumn> getColumns() {
      return columns;
    }
    public void checkResultSet(ResultSetMetaData rsmd) throws SQLException {
      if (columns.isEmpty()) {
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
          SQLControlColumn col = new SQLControlColumn();
          col.setType(SQLControlColumnType.Auto);
          col.setTitle(rsmd.getColumnName(i));     
          col.setSourceIndex(i);
          columns.add(col);
        }
      } else {
        for (SQLControlColumn col : columns) {
          col.sourceIndex = getColByName(rsmd, col.source);
          if (col.sourceIndex == 0) {
            throw new FHIRException("Unable to find column "+col.source+" in sql query result set");              
          }
          if (col.target != null) {
            col.targetIndex = getColByName(rsmd, col.target);
          }
          if (col.system != null) {
            col.systemIndex = getColByName(rsmd, col.system);
          }
          if (col.display != null) {
            col.displayIndex = getColByName(rsmd, col.display);
          }
          if (col.version != null) {
            col.versionIndex = getColByName(rsmd, col.version);
          }
        }
      } 
    }
    private int getColByName(ResultSetMetaData rsmd, String source) throws SQLException {
      for (int i = 1; i <= rsmd.getColumnCount(); i++) {
        if (source.equals(rsmd.getColumnName(i))) {
          return i;
        }
      }
      return 0;
    }
  }

  private boolean debug = true;
  
//  public enum RenderingType {
//    AutoDetect, None, String, Markdown, Url, Coding;
//  }
//
//  public class RenderingRule {
//    private RenderingType type;
//    private int linkSource;
//    private int linksCol;
//    private int displayCol;
//    private int displaySource;
//    private String system;
//    protected RenderingRule(RenderingType type) {
//      super();
//      this.type = type;
//    }
//  }

  private IWorkerContext context;
  private RenderingContext rc;
  private ContextUtilities cu;
  private Connection con;
  private Set<String> errors = new HashSet<>();
  private MarkDownProcessor md = new MarkDownProcessor(Dialect.COMMON_MARK);
  private List<CodeSystem> codesystems = new ArrayList<>();
  private List<ConceptMap> mappings = new ArrayList<>();
  private List<FetchedFile> files;
  private int lastMDKey;
  private int lastResKey;
  private int lastPropKey;
  private int lastCPropKey;
  private int lastConceptKey;
  private int lastDesgKey;
  private int lastMapKey;
  private int lastVSKey;
  private int lastCLKey;
  private int lastVLKey;

  private long cumulativeTime;
  
  private void time(long start) {
    cumulativeTime = cumulativeTime + (System.currentTimeMillis() - start);
  }
  
  public DBBuilder(String path, IWorkerContext context, RenderingContext rc, ContextUtilities cu, List<FetchedFile> files) {
    this.context = context;
    this.rc = rc;
    this.cu = cu;
    this.files = files;
    long start = System.currentTimeMillis();
    try {
      con = connect(path);
    } catch (Exception e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
      con = null;
    }
    time(start);
  }

  public void metadata(String name, String value)  {
    long start = System.currentTimeMillis();
    if (con == null) {
      return;
    }

    try {
      PreparedStatement psql = con.prepareStatement("Insert into Metadata (key, name, value) values (?, ?, ?)");
      psql.setInt(1, ++lastMDKey);
      bindString(psql, 2, name);
      bindString(psql, 3, value);
      psql.executeUpdate();
    } catch (SQLException e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
    }
    time(start);
  }

  public void saveResource(FetchedFile f, FetchedResource r, byte[] json) {
    long start = System.currentTimeMillis();
    if (con == null) {
      return;
    }

    try {
      if (r.getResource() == null || !(r.getResource() instanceof CanonicalResource)) {
        PreparedStatement psql = con.prepareStatement("Insert into Resources (key, type, custom, id, web, url, version, status, date, name, title, description, json) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        psql.setInt(1, ++lastResKey);
        bindString(psql, 2, r.fhirType());
        psql.setInt(3, r.getElement().getProperty().getStructure().hasUserData(UserDataNames.loader_custom_resource) ? 1 : 0);
        bindString(psql, 4, r.getId());
        bindString(psql, 5, r.getElement().getWebPath());
        bindString(psql, 6, getSingleChildValue(r, "url", null));
        bindString(psql, 7, getSingleChildValue(r, "version", null));
        bindString(psql, 8, getSingleChildValue(r, "status", null));
        bindString(psql, 9, getSingleChildValue(r, "date", null));
        bindString(psql, 10, getSingleChildValue(r, "name", r.getResourceName()));
        bindString(psql, 11, getSingleChildValue(r, "title", null));
        bindString(psql, 12, r.getResourceDescription());
        psql.setBytes(13, json);
        psql.executeUpdate();   
        r.getElement().setUserData(UserDataNames.db_key, lastResKey);
        r.getElement().setUserData(UserDataNames.Storage_key, lastResKey);
      } else {
        CanonicalResource cr = (CanonicalResource) r.getResource();
        PreparedStatement psql = con.prepareStatement("Insert into Resources (key, type, custom, id, web, url, version, status, date, name, title, experimental, realm, description, purpose, copyright, copyrightLabel, standardStatus, derivation, kind, sdType, base, content, supplements, json) "+
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        psql.setInt(1, ++lastResKey);
        bindString(psql, 2,  r.fhirType());
        psql.setInt(3, r.getElement().getProperty().getStructure().hasUserData(UserDataNames.loader_custom_resource) ? 1 : 0);
        bindString(psql, 4,  r.getId());
        bindString(psql, 5,  cr.getWebPath());
        bindString(psql, 6,  cr.getUrl());
        bindString(psql, 7,  cr.getVersion());
        bindString(psql, 8,  cr.getStatus().toCode());
        bindString(psql, 9,  cr.getDateElement().primitiveValue());
        bindString(psql, 10,  cr.getName());
        bindString(psql, 11,  cr.getTitle());
        bindString(psql, 12,  cr.getExperimentalElement().primitiveValue());
        bindString(psql, 13, realm(cr));
        bindString(psql, 14, cr.getDescription());
        bindString(psql, 15, cr.getPurpose());
        bindString(psql, 16, cr.getCopyright());
        bindString(psql, 17, cr.getCopyrightLabel());
        bindString(psql, 18, ExtensionUtilities.getStandardsStatus(cr) == null ? null : ExtensionUtilities.getStandardsStatus(cr).toCode());
        bindString(psql, 19, derivation(cr));
        bindString(psql, 20, kind(cr));
        bindString(psql, 21, sdType(cr));
        bindString(psql, 22, base(cr));
        bindString(psql, 23, content(cr));
        bindString(psql, 24, supplements(cr));
        psql.setBytes(25, json);
        psql.executeUpdate();    
        if (cr instanceof CodeSystem) {
          codesystems.add((CodeSystem) cr);
        } else if (cr instanceof ConceptMap) {
          mappings.add((ConceptMap) cr);
        }
        cr.setUserData(UserDataNames.db_key, lastResKey);
        cr.setUserData(UserDataNames.Storage_key, lastResKey);
        r.getElement().setUserData(UserDataNames.Storage_key, lastResKey);
      } 
    } catch (SQLException e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
    }
    time(start);
  }

  private String derivation(CanonicalResource cr) {
    if (cr instanceof StructureDefinition) {
      return ((StructureDefinition) cr).getDerivation().toCode();
    } else {
      return null;
    }
  }

  private String kind(CanonicalResource cr) {
    if (cr instanceof StructureDefinition) {
      return ((StructureDefinition) cr).getKind().toCode();
    } else {
      return null;
    }
  }

  private String sdType(CanonicalResource cr) {
    if (cr instanceof StructureDefinition) {
      return ((StructureDefinition) cr).getType();
    } else {
      return null;
    }
  }

  private String base(CanonicalResource cr) {
    if (cr instanceof StructureDefinition) {
      return ((StructureDefinition) cr).getBaseDefinition();
    } else {
      return null;
    }
  }

  private String content(CanonicalResource cr) {
    if (cr instanceof CodeSystem) {
      return ((CodeSystem) cr).getContent().toCode();
    } else {
      return null;
    }
  }

  private String supplements(CanonicalResource cr) {
    if (cr instanceof CodeSystem) {
      return ((CodeSystem) cr).getSupplements();
    } else {
      return null;
    }
  }

  private String getSingleChildValue(FetchedResource r, String name, String defaultValue) {
    return r.getElement().getChildren(name).size() == 1 && r.getElement().getNamedChild(name).isPrimitive() ? r.getElement().getNamedChildValue(name) : defaultValue;
  }

  public void finishResources() {
    long start = System.currentTimeMillis();
    if (con == null) {
      return;
    }

    try {
      PreparedStatement psql = con.prepareStatement("Insert into Properties (Key, ResourceKey, Code, Uri, Description, Type) "+
          "values (?, ?, ?, ?, ?, ?)");
      for (CodeSystem cs : codesystems) {
        for (PropertyComponent p : cs.getProperty()) { 
          psql.setInt(1, ++lastPropKey);
          psql.setInt(2, ((Integer) cs.getUserData(UserDataNames.db_key)).intValue());
          bindString(psql, 3, p.getCode());
          bindString(psql, 4, p.getUri());
          bindString(psql, 5, p.getDescription());
          bindString(psql, 6, p.getType().toCode());  
          psql.executeUpdate();     
          p.setUserData(UserDataNames.db_key, lastPropKey);   
        }
      }
      psql = con.prepareStatement("Insert into Concepts (Key, ResourceKey, ParentKey,  Code, Display, Definition) "+
          "values (?, ?, ?, ?, ?, ?)");
      for (CodeSystem cs : codesystems) {
        addConcepts(cs, cs.getConcept(), psql, 0);
      }
      psql = con.prepareStatement("Insert into ConceptProperties (Key, ResourceKey, ConceptKey, PropertyKey, Code, Value) "+
          "values (?, ?, ?, ?, ?, ?)");
      for (CodeSystem cs : codesystems) {
        addConceptProperties(cs, cs.getConcept(), psql);
      }
      psql = con.prepareStatement("Insert into Designations (Key, ResourceKey, ConceptKey, UseSystem, UseCode, Lang, Value) "+
          "values (?, ?, ?, ?, ?, ?, ?)");
      for (CodeSystem cs : codesystems) {
        addConceptDesignations(cs, cs.getConcept(), psql);
      }

      psql = con.prepareStatement("Insert into ConceptMappings (Key, ResourceKey, SourceSystem, SourceVersion, SourceCode, Relationship, TargetSystem, TargetVersion, TargetCode) "+
          "values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
      for (ConceptMap cm : mappings) {
        for (ConceptMapGroupComponent grp : cm.getGroup()) {
          for (SourceElementComponent src : grp.getElement()) {
            for (TargetElementComponent tgt : src.getTarget()) {
              psql.setInt(1, ++lastMapKey);
              psql.setInt(2, ((Integer) cm.getUserData(UserDataNames.db_key)).intValue());
              bindString(psql, 3, grp.getSourceElement().baseUrl());
              bindString(psql, 4, grp.getSourceElement().version());
              bindString(psql, 5, src.getCode());
              bindString(psql, 6, tgt.getRelationshipElement().primitiveValue());
              bindString(psql, 7, grp.getTargetElement().baseUrl());
              bindString(psql, 8, grp.getTargetElement().version());
              bindString(psql, 9, tgt.getCode());
              psql.executeUpdate();    
            }
          }
        }
      }
    } catch (SQLException e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
    }
    time(start);
  }

  public void recordExpansion(ValueSet vs, ValueSetExpansionOutcome exp) throws SQLException {
    long start = System.currentTimeMillis();
    try {
      if (con == null) {
        return;
      }
      if (exp == null || exp.getValueset() == null) {
        return;
      }

      PreparedStatement psql = con.prepareStatement("Insert into ValueSet_Codes (Key, ResourceKey, ValueSetUri, ValueSetVersion, System, Version, Code, Display) "+
          "values (?, ?, ?, ?, ?, ?, ?, ?)");
      for (ValueSetExpansionContainsComponent e : exp.getValueset().getExpansion().getContains()) {
        addContains(vs, e, psql);
      }
    } catch (SQLException e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
    }
    time(start);
  }


  private void addContains(ValueSet vs, ValueSetExpansionContainsComponent e, PreparedStatement psql) throws SQLException {
    if (vs.hasUserData(UserDataNames.db_key)) {
      psql.setInt(1, ++lastVSKey);
      psql.setInt(2, ((Integer) vs.getUserData(UserDataNames.db_key)).intValue());
      bindString(psql, 3, vs.getUrl());
      bindString(psql, 4, vs.getVersion());
      bindString(psql, 5, e.getSystem());
      bindString(psql, 6, e.getVersion());
      bindString(psql, 7, e.getCode());
      bindString(psql, 8, e.getDisplay());
      psql.executeUpdate();   
      for (ValueSetExpansionContainsComponent c : e.getContains()) {
        addContains(vs, c, psql);
      }
    }
  }

  private void addConcepts(CodeSystem cs, List<ConceptDefinitionComponent> list, PreparedStatement psql, int parent) throws SQLException {
    if (cs.hasUserData(UserDataNames.db_key)) {
    for (ConceptDefinitionComponent cd : list) {
      psql.setInt(1, ++lastConceptKey);
      psql.setInt(2, ((Integer) cs.getUserData(UserDataNames.db_key)).intValue());
      if (parent == 0) {
        psql.setNull(3, java.sql.Types.INTEGER);
      } else {
        psql.setInt(3, parent);
      }
      bindString(psql, 4, cd.getCode());
      bindString(psql, 5, cd.getDisplay());
      bindString(psql, 6, cd.getDefinition());
      psql.executeUpdate();    
      cd.setUserData(UserDataNames.db_key, lastConceptKey);   
      addConcepts(cs, cd.getConcept(), psql, lastConceptKey);
    }
    }
  }

  private void addConceptProperties(CodeSystem cs, List<ConceptDefinitionComponent> list, PreparedStatement psql) throws SQLException {
    if (cs.hasUserData(UserDataNames.db_key)) {
       for (ConceptDefinitionComponent cd : list) {
      for (ConceptPropertyComponent p : cd.getProperty()) { 
        psql.setInt(1, ++lastCPropKey);
        psql.setInt(2, ((Integer) cs.getUserData(UserDataNames.db_key)).intValue());
        psql.setInt(3, ((Integer) cd.getUserData(UserDataNames.db_key)).intValue());
        PropertyComponent pd = getPropDefn(p.getCode(), cs);
        if (pd == null) {
          psql.setNull(4, java.sql.Types.INTEGER);
        } else {
          psql.setInt(4, ((Integer) pd.getUserData(UserDataNames.db_key)).intValue());
        }
        bindString(psql, 5, p.getCode());
        bindString(psql, 6, p.getValue() == null ? p.getValue().primitiveValue() : null);
        psql.executeUpdate();    
        p.setUserData(UserDataNames.db_key, lastCPropKey);   
      }
      addConceptProperties(cs, cd.getConcept(), psql);
    }
    }
  }

  private void addConceptDesignations(CodeSystem cs, List<ConceptDefinitionComponent> list, PreparedStatement psql) throws SQLException {
    if (cs.hasUserData(UserDataNames.db_key)) {
      for (ConceptDefinitionComponent cd : list) {
        for (ConceptDefinitionDesignationComponent p : cd.getDesignation()) { 
          psql.setInt(1, ++lastDesgKey);
          psql.setInt(2, ((Integer) cs.getUserData(UserDataNames.db_key)).intValue());
          psql.setInt(3, ((Integer) cd.getUserData(UserDataNames.db_key)).intValue());  
          if (p.hasUse()) {
            bindString(psql, 4, p.getUse().getSystem());
            bindString(psql, 5, p.getUse().getCode());
          } else {
            bindString(psql, 4, null);
            bindString(psql, 5, null);            
          }
          bindString(psql, 6, p.getLanguage());
          bindString(psql, 7, p.getValue());
          psql.executeUpdate();    
          p.setUserData(UserDataNames.db_key, lastDesgKey);   
        }
        addConceptDesignations(cs, cd.getConcept(), psql);
      }
    }
  }

  private void bindString(PreparedStatement psql, int i, String s) throws SQLException {
    if (s == null) {
      psql.setNull(i, java.sql.Types.NVARCHAR);
    } else {
      psql.setString(i, s);
    }
    
  }

  private PropertyComponent getPropDefn(String code, CodeSystem cs) {
    if (code == null) {
      return null;
    }
    for (PropertyComponent p : cs.getProperty()) {
      if (code.equals(p.getCode())) {
        return p;
      }
    }
    return null;
  }

  private String realm(CanonicalResource cr) {
    return null;
  }

  private Connection connect(String filename) throws SQLException, ClassNotFoundException {
    new File(filename).delete();
    Connection con = DriverManager.getConnection("jdbc:sqlite:"+filename); 
    makeMetadataTable(con);
    makeResourcesTable(con);
    makePropertiesTable(con);
    makeConceptsTable(con);
    makeConceptPropertiesTable(con);
    makeDesignationsTable(con);
    makeMappingsTable(con);
    makeValueSetTable(con);
    makeCSListTables(con);
    makeVSListTables(con);
    return con;    
  }

  private void makeValueSetTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ValueSet_Codes (\r\n"+
        "Key             integer NOT NULL,\r\n"+
        "ResourceKey     integer NOT NULL,\r\n"+
        "ValueSetUri     nvarchar NOT NULL,\r\n"+
        "ValueSetVersion nvarchar NOT NULL,\r\n"+
        "System          nvarchar NOT NULL,\r\n"+
        "Version         nvarchar NULL,\r\n"+
        "Code            nvarchar NOT NULL,\r\n"+
        "Display        nvarchar NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeCSListTables(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE CodeSystemList (\r\n"+
        "CodeSystemListKey integer NOT NULL,\r\n"+
        "ViewType          integer NOT NUll,\r\n"+
        "ResourceKey       integer NULL,\r\n"+
        "Url               nvarchar NULL,\r\n"+
        "Version           nvarchar NULL,\r\n"+
        "Status            nvarchar NULL,\r\n"+
        "Name              nvarchar NULL,\r\n"+
        "Title             nvarchar NULL,\r\n"+
        "Description       nvarchar NULL,\r\n"+
        "PRIMARY KEY (CodeSystemListKey))\r\n");
    stmt.execute("CREATE TABLE CodeSystemListOIDs (\r\n"+
        "CodeSystemListKey integer NOT NULL,\r\n"+
        "OID               nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (CodeSystemListKey,OID))\r\n");
    stmt.execute("CREATE TABLE CodeSystemListRefs (\r\n"+
        "CodeSystemListKey integer NOT NULL,\r\n"+
        "Type              nvarchar NOT NULL,\r\n"+
        "Id                nvarchar NOT NULL,\r\n"+
        "ResourceKey       integer NULL,\r\n"+
        "Title             nvarchar NULL,\r\n"+
        "Web               nvarchar NULL,\r\n"+
        "PRIMARY KEY (CodeSystemListKey,Type,Id))\r\n");
  }
  
  private void makeVSListTables(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ValueSetList (\r\n"+
        "ValueSetListKey   integer NOT NULL,\r\n"+
        "ViewType          integer NOT NUll,\r\n"+
        "ResourceKey       integer NULL,\r\n"+
        "Url               nvarchar NULL,\r\n"+
        "Version           nvarchar NULL,\r\n"+
        "Status            nvarchar NULL,\r\n"+
        "Name              nvarchar NULL,\r\n"+
        "Title             nvarchar NULL,\r\n"+
        "Description       nvarchar NULL,\r\n"+
        "PRIMARY KEY (ValueSetListKey))\r\n");
    stmt.execute("CREATE TABLE ValueSetListOIDs (\r\n"+
        "ValueSetListKey   integer NOT NULL,\r\n"+
        "OID               nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (ValueSetListKey,OID))\r\n");
    stmt.execute("CREATE TABLE ValueSetListSystems (\r\n"+
        "ValueSetListKey   integer NOT NULL,\r\n"+
        "URL               nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (ValueSetListKey,URL))\r\n");
    stmt.execute("CREATE TABLE ValueSetListSources (\r\n"+
        "ValueSetListKey   integer NOT NULL,\r\n"+
        "Source            nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (ValueSetListKey,Source))\r\n");
    stmt.execute("CREATE TABLE ValueSetListRefs (\r\n"+
        "ValueSetListKey   integer NOT NULL,\r\n"+
        "Type              nvarchar NOT NULL,\r\n"+
        "Id                nvarchar NOT NULL,\r\n"+
        "ResourceKey       integer NULL,\r\n"+
        "Title             nvarchar NULL,\r\n"+
        "Web               nvarchar NULL,\r\n"+
        "PRIMARY KEY (ValueSetListKey,Type,Id))\r\n");
  }

  private void makeMetadataTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Metadata (\r\n"+
        "Key    integer NOT NULL,\r\n"+
        "Name   nvarchar NOT NULL,\r\n"+
        "Value  nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeResourcesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Resources (\r\n"+
        "Key             integer NOT NULL,\r\n"+
        "Type            nvarchar NOT NULL,\r\n"+
        "Custom          integer NOT NULL,\r\n"+
        "Id              nvarchar NOT NULL,\r\n"+
        "Web             nvarchar NOT NULL,\r\n"+
        "Url             nvarchar NULL,\r\n"+
        "Version         nvarchar NULL,\r\n"+
        "Status          nvarchar NULL,\r\n"+
        "Date            nvarchar NULL,\r\n"+
        "Name            nvarchar NULL,\r\n"+
        "Title           nvarchar NULL,\r\n"+
        "Experimental    nvarchar NULL,\r\n"+
        "Realm           nvarchar NULL,\r\n"+
        "Description     nvarchar NULL,\r\n"+
        "Purpose         nvarchar NULL,\r\n"+
        "Copyright       nvarchar NULL,\r\n"+
        "CopyrightLabel  nvarchar NULL,\r\n"+
        "derivation      nvarchar NULL,\r\n"+
        "standardStatus  nvarchar NULL,\r\n"+
        "kind            nvarchar NULL,\r\n"+
        "sdType          nvarchar NULL,\r\n"+
        "base            nvarchar NULL,\r\n"+
        "content         nvarchar NULL,\r\n"+
        "supplements     nvarchar NULL,\r\n"+
        "Json            nvarchar NOT NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makePropertiesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Properties (\r\n"+
        "Key          integer NOT NULL,\r\n"+
        "ResourceKey  integer NOT NULL,\r\n"+
        "Code         varchar NOT NULL,\r\n"+
        "Uri          varchar NULL,\r\n"+
        "Description  varchar NULL,\r\n"+
        "Type         varchar NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeConceptsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Concepts (\r\n"+
        "Key          integer NOT NULL,\r\n"+
        "ResourceKey  integer NOT NULL,\r\n"+
        "ParentKey    integer NULL,\r\n"+
        "Code         varchar NULL,\r\n"+
        "Display      varchar NULL,\r\n"+
        "Definition   varchar NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeConceptPropertiesTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ConceptProperties (\r\n"+
        "Key          integer NOT NULL,\r\n"+
        "ResourceKey  integer NOT NULL,\r\n"+
        "ConceptKey   integer NOT NULL,\r\n"+
        "PropertyKey  integer NULL,\r\n"+
        "Code         varchar NULL,\r\n"+
        "Value        varchar NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeDesignationsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE Designations (\r\n"+
        "Key          integer NOT NULL,\r\n"+
        "ResourceKey  integer NOT NULL,\r\n"+
        "ConceptKey   integer NOT NULL,\r\n"+
        "UseSystem    varchar NULL,\r\n"+
        "UseCode      varchar NULL,\r\n"+
        "Lang         varchar NULL,\r\n"+
        "Value        text NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeMappingsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ConceptMappings (\r\n"+
        "Key           integer NOT NULL,\r\n"+
        "ResourceKey   integer NOT NULL,\r\n"+
        "SourceSystem  varchar NULL,\r\n"+
        "SourceVersion varchar NULL,\r\n"+
        "SourceCode    varchar NULL,\r\n"+
        "Relationship  varchar NULL,\r\n"+
        "TargetSystem  varchar NULL,\r\n"+
        "TargetVersion varchar NULL,\r\n"+
        "TargetCode    varchar NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }


  public String processSQL(String sql) {
    long start = System.currentTimeMillis();
    if (con == null) {
      return "<span style=\"color: maroon\">Error processing SQL: SQL is not set up properly</span>";
    }

    try {
      if (sql == null) {
        throw new IllegalArgumentException("Param sql cannot be null.");
      }

      SQLControl ctrl = new SQLControl();
      if (sql.startsWith("{")) {
        JsonObject json = org.hl7.fhir.utilities.json.parser.JsonParser.parseObject(sql);
        ctrl.setQuery(json.asString("query"));
        if (json.has("class")) {
          ctrl.setClss(json.asString("class"));
        } else {
          ctrl.setClss("grid");      
        }
        if (json.has("titles")) {
          ctrl.setTitles(json.asBoolean("titles"));
        } else {
          ctrl.setTitles(true);      
        }
        for (JsonObject colj : json.forceArray("columns").asJsonObjects()) {
          SQLControlColumn col = new SQLControlColumn();
          ctrl.getColumns().add(col);
          if (colj.has("type")) {
            col.setType(SQLControlColumnType.fromCode(colj.asString("type")));
          } else {
            col.setType(SQLControlColumnType.Auto);
          }
          col.setTitle(colj.asString("title"));
          if (colj.has("source")) {
            col.setSource(colj.asString("source"));
          } else {
            col.setSource(colj.asString("title"));
          }
          if (colj.has("title")) {
            col.setTitle(colj.asString("title"));
          } else {
            col.setTitle(colj.asString("source"));
          }
          if (col.getSource() == null) {
            throw new FHIRException("A source column is required");
          }
          col.setTarget(colj.asString("target"));
          col.setSystem(colj.asString("system"));
          col.setDisplay(colj.asString("display"));
          col.setVersion(colj.asString("version"));      
        }
      } else {
        ctrl.setQuery(sql);
        ctrl.setClss("grid");
        ctrl.setTitles(true);
      }
      
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(ctrl.getQuery());
      ResultSetMetaData rsmd = rs.getMetaData();
      ctrl.checkResultSet(rsmd);

      String plainText = null;
      int fc = 0;
      XhtmlNode tbl = new XhtmlNode(NodeType.Element, "table").attribute("class", ctrl.getClss());
      XhtmlNode tr = tbl.tr();
      if (ctrl.isTitles()) {
        for (SQLControlColumn col : ctrl.getColumns()) {
          tr.td().style("background-color: #eeeeee").tx(col.getTitle());          
        }
      }

      while (rs.next()) {
        tr = tbl.tr();
        for (SQLControlColumn col : ctrl.getColumns()) {    
          XhtmlNode td = tr.td();
          String s = rs.getString(col.getSourceIndex());
          fc++;
          plainText = s;
          if (!Utilities.noString(s)) {
            switch (col.getType()) {
            case Auto: 
              Resource r = context.fetchResource(Resource.class, s);
              if (r != null && r instanceof CanonicalResource && r.hasWebPath()) {
                String d = ((CanonicalResource) r).present();
                if (Utilities.noString(d)) {
                  d = r.getId();
                }
                td.ah(s).tx(d);
              } else if (Utilities.isAbsoluteUrlLinkable(s)) {
                td.ah(s).tx(s);
              } else if (md.isProbablyMarkdown(s, true)) {
                td.markdown(s, "sql");
              } else {
                td.tx(s);
              }
              break;
            case Canonical:
              r = context.fetchResource(Resource.class, s);
              if (r != null && r instanceof CanonicalResource && r.hasWebPath()) {
                String d = ((CanonicalResource) r).present();
                if (Utilities.noString(d)) {
                  d = r.getId();
                }
                td.ah(s).tx(d);
              } else {
                td.ah(s).tx(s);
              } 
              break;
            case Link:
              String t = s;
              if (col.getTargetIndex() > 0) {
                t = rs.getString(col.getTargetIndex());
              }
              td.ah(t).tx(s);
              break;
            case Coding:
              Coding c = new Coding();
              c.setCode(s);
              if (col.getSystemIndex() > 0) {
                c.setSystem(rs.getString(col.getSystemIndex()));
              } else {
                c.setSystem(col.getSystem());              
              }
              if (col.getDisplayIndex() > 0) {
                c.setDisplay(rs.getString(col.getDisplayIndex()));
              }
              if (col.getVersionIndex() > 0) {
                c.setVersion(rs.getString(col.getVersionIndex()));
              }
              new DataRenderer(rc).renderDataType(new RenderingStatus(), td, ResourceWrapper.forType(cu, c));
              break;
            case Markdown:
              td.markdown(s, "sql");
              break;
            case Url:
              td.ah(s).tx(s);
              break;
            case Text:
              td.tx(s);
              break;
            case Resource:
              r = context.fetchResource(Resource.class, s);
              if (r != null && r instanceof CanonicalResource && r.hasWebPath()) {
                String d = ((CanonicalResource) r).present();
                if (Utilities.noString(d)) {
                  d = r.getId();
                }
                td.ah(s).tx(d);
              } else {
                // it's not a canonical, in which case we'll decide it's a file name, and find the resource that points to, and then find it's name if we can have on
                FetchedResource tgt = null;
                for (FetchedFile f : files) {
                  for (FetchedResource rf : f.getResources()) {
                    String key = rf.getElement().getUserString(UserDataNames.Storage_key);
                    if (s.equals(key)) {
                      tgt = rf;
                    }
                  }
                }
                if (tgt != null) {
                  td.ah(tgt.getElement().getWebPath()).tx(tgt.getBestName());
                } else {
                  td.ah(s).tx(s);
                }
              }
              break;
            default:
              td.tx(s);
              break;
            }
          }
        }
      }
      time(start);
      if (fc == 1 && plainText != null) {
        return plainText;
      } else {
        return new XhtmlComposer(true, false).compose(tbl);
      }
    } catch (Exception e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
      time(start);
      return "<span style=\"color: maroon\">Error processing SQL: "+Utilities.escapeXml(e.getMessage())+"</span>";
    }
  }

  public String executeQueryToJson(String sql) throws SQLException {
    if (con == null) {
      throw new IllegalStateException("No database connection available.");
    }

    if (sql == null) {
      throw new IllegalArgumentException("Param sql cannot be null.");
    }

    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery(sql);
    ResultSetMetaData rsmd = rs.getMetaData();

    JsonArray jsonArray = new JsonArray();

    while (rs.next()) {
      JsonObject jsonObject = new JsonObject();
      for (int i = 1; i <= rsmd.getColumnCount(); i++) {
        String colName = rsmd.getColumnName(i);
        Object value = rs.getObject(i);

        if (value == null) {
          jsonObject.add(colName, new JsonNull());
        } else if (value instanceof String) {
          jsonObject.add(colName, new JsonString((String) value));
        } else if (value instanceof Integer || value instanceof Long) {
          jsonObject.add(colName, new JsonNumber(value.toString()));
        } else if (value instanceof Boolean) {
          jsonObject.add(colName, new JsonBoolean((Boolean) value));
        } else {
          jsonObject.add(colName, new JsonString(value.toString()));
        }
      }
      jsonArray.add(jsonObject);
    }

    return jsonArray.toString();
  }

  public void closeUp() {
    long start = System.currentTimeMillis();
    if (con != null) {
      try {
        con.close();
      } catch (SQLException e) {
        errors.add(e.getMessage());
        if (debug) {
          e.printStackTrace();
        }
      }
    }
    if (!errors.isEmpty()) {
      System.out.println("Errors happened trying to build SQL database or process SQL injections:");
      for (String s : errors) {
        System.out.println("  "+s);        
      }
    }
    time(start);
    System.out.println("DB Cumulative Time invested: "+Utilities.describeDuration(cumulativeTime));
  }

  public void addToCSList(int viewType, CodeSystem cs, Set<String> oids, Set<Resource> rl) {
    try {
      lastCLKey++;
      PreparedStatement sql;
      sql = con.prepareStatement("insert into CodeSystemList (CodeSystemListKey, ViewType, ResourceKey, Url, Version, Status, Name, Title, Description) values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sql.setInt(1, lastCLKey);
      sql.setInt(2, viewType);
      if (cs.hasUserData(UserDataNames.db_key)) {
        sql.setInt(3, (int) cs.getUserData(UserDataNames.db_key));
      } else {
        sql.setNull(3, java.sql.Types.INTEGER);
      }
      sql.setString(4, cs.getUrl());
      sql.setString(5, cs.getVersion());
      sql.setString(6, cs.hasStatus() ? cs.getStatus().toCode() : null);
      sql.setString(7, cs.getName());
      sql.setString(8, cs.getTitle());
      sql.setString(9, cs.getDescription());
      sql.execute();

      sql = con.prepareStatement("insert into CodeSystemListOIDs (CodeSystemListKey, OID) values (?, ?)");
      for (String oid : oids) {
        sql.setInt(1, lastCLKey);
        sql.setString(2, oid);      
        sql.execute();
      }

      if (rl != null) {
        Set<String> keys = new HashSet<>();
        sql = con.prepareStatement("insert into CodeSystemListRefs (CodeSystemListKey, Type, Id, ResourceKey, Title, Web) values (?, ?, ?, ?, ?, ?)");      
        for (Resource r : rl) {
          String key = r.fhirType()+"/"+r.getIdBase();
          if (!keys.contains(key) && r.getIdBase() != null) {
            keys.add(key);
            sql.setInt(1, lastCLKey);
            sql.setString(2, r.fhirType());      
            sql.setString(3, r.getIdBase());
            if (cs.hasUserData(UserDataNames.db_key)) {
              sql.setInt(4, (int) cs.getUserData(UserDataNames.db_key));
            } else {
              sql.setNull(4, java.sql.Types.INTEGER);
            }      
            sql.setString(5, r instanceof CanonicalResource ? ((CanonicalResource) r).present() : r.fhirType()+"/"+r.getIdBase());
            sql.setString(6, r.getWebPath());
            sql.execute();
          }
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public void addToVSList(int viewType, ValueSet vs, Set<String> oids, Set<String> used, Set<String> sources, Set<Resource> rl) {
    try {
      lastVLKey++;
      PreparedStatement sql;
      sql = con.prepareStatement("insert into ValueSetList (ValueSetListKey, ViewType, ResourceKey, Url, Version, Status, Name, Title, Description) values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
      sql.setInt(1, lastVLKey);
      sql.setInt(2, viewType);
      if (vs.hasUserData(UserDataNames.db_key)) {
        sql.setInt(3, (int) vs.getUserData(UserDataNames.db_key));
      } else {
        sql.setNull(3, java.sql.Types.INTEGER);
      }
      sql.setString(4, vs.getUrl());
      sql.setString(5, vs.getVersion());
      sql.setString(6, vs.hasStatus() ? vs.getStatus().toCode() : null);
      sql.setString(7, vs.getName());
      sql.setString(8, vs.getTitle());
      sql.setString(9, vs.getDescription());
      sql.execute();

      sql = con.prepareStatement("insert into ValueSetListOIDs (ValueSetListKey, OID) values (?, ?)");
      for (String oid : oids) {
        sql.setInt(1, lastVLKey);
        sql.setString(2, oid);      
        sql.execute();
      }

      sql = con.prepareStatement("insert into ValueSetListSystems (ValueSetListKey, URL) values (?, ?)");
      for (String u : used) {
        sql.setInt(1, lastVLKey);
        sql.setString(2, u);      
        sql.execute();
      }


      sql = con.prepareStatement("insert into ValueSetListSources (ValueSetListKey, Source) values (?, ?)");
      for (String s : sources) {
        sql.setInt(1, lastVLKey);
        sql.setString(2, s);      
        sql.execute();
      }

      if (rl != null) {
        Set<String> ids = new HashSet<>();
        sql = con.prepareStatement("insert into ValueSetListRefs (ValueSetListKey, Type, Id, ResourceKey, Title, Web) values (?, ?, ?, ?, ?, ?)");      
        for (Resource r : rl) {
          if (!ids.contains(r.getIdBase())) {
            ids.add(r.getIdBase());
            sql.setInt(1, lastVLKey);
            sql.setString(2, r.fhirType());      
            sql.setString(3, r.getIdBase());
            if (vs.hasUserData(UserDataNames.db_key)) {
              sql.setInt(4, (int) vs.getUserData(UserDataNames.db_key));
            } else {
              sql.setNull(4, java.sql.Types.INTEGER);
            }      
            sql.setString(5, r instanceof CanonicalResource ? ((CanonicalResource) r).present() : r.fhirType()+"/"+r.getIdBase());
            sql.setString(6, r.getWebPath());
            sql.execute();
          }
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    
  }

  public Connection getConnection() {
    return con;
  }



}
