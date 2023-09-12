package org.hl7.fhir.igtools.renderers;

import java.io.File;
import java.io.IOException;
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
import org.hl7.fhir.igtools.renderers.DBBuilder.RenderingRule;
import org.hl7.fhir.igtools.renderers.DBBuilder.RenderingType;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionComponent;
import org.hl7.fhir.r5.model.CodeSystem.ConceptDefinitionDesignationComponent;
import org.hl7.fhir.r5.model.CodeSystem.ConceptPropertyComponent;
import org.hl7.fhir.r5.model.CodeSystem.PropertyComponent;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r5.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r5.model.ConceptMap.TargetElementComponent;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.model.ValueSet.ValueSetExpansionContainsComponent;
import org.hl7.fhir.r5.terminologies.expansion.ValueSetExpansionOutcome;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

public class DBBuilder {
  private boolean debug = true;
  
  public enum RenderingType {
    AutoDetect, None, String, Markdown;
  }

  public class RenderingRule {
    private RenderingType type;
    private int linkCol;
    private int linksCol;
    protected RenderingRule(RenderingType type) {
      super();
      this.type = type;
    }
  }

  private Connection con;
  private Set<String> errors = new HashSet<>();
  private MarkDownProcessor md = new MarkDownProcessor(Dialect.COMMON_MARK);
  private List<CodeSystem> codesystems = new ArrayList<>();
  private List<ConceptMap> mappings = new ArrayList<>();
  private int lastMDKey;
  private int lastResKey;
  private int lastPropKey;
  private int lastCPropKey;
  private int lastConceptKey;
  private int lastDesgKey;
  private int lastMapKey;
  private int lastVSKey;

  private long cumulativeTime;
  
  private void time(long start) {
    cumulativeTime = cumulativeTime + (System.currentTimeMillis() - start);
  }
  
  public DBBuilder(String path) {
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
        PreparedStatement psql = con.prepareStatement("Insert into Resources (key, type, id, json, web) values (?, ?, ?, ?, ?)");
        psql.setInt(1, ++lastResKey);
        bindString(psql, 2, r.fhirType());
        bindString(psql, 3, r.getId());
        bindString(psql, 4, r.getLocalRef());
        psql.setBytes(5, json);
        psql.executeUpdate();   
        r.getElement().setUserData("db.key", lastResKey);
      } else {
        CanonicalResource cr = (CanonicalResource) r.getResource();
        PreparedStatement psql = con.prepareStatement("Insert into Resources (key, type, id, web, url, version, status, date, name, title, experimental, realm, description, purpose, copyright, copyrightLabel, json) "+
            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        psql.setInt(1, ++lastResKey);
        bindString(psql, 2,  r.fhirType());
        bindString(psql, 3,  r.getId());
        bindString(psql, 4,  cr.getWebPath());
        bindString(psql, 5,  cr.getUrl());
        bindString(psql, 6,  cr.getVersion());
        bindString(psql, 7,  cr.getStatus().toCode());
        bindString(psql, 8,  cr.getDateElement().primitiveValue());
        bindString(psql, 9,  cr.getName());
        bindString(psql, 10,  cr.getTitle());
        bindString(psql, 11,  cr.getExperimentalElement().primitiveValue());
        bindString(psql, 12, realm(cr));
        bindString(psql, 13, cr.getDescription());
        bindString(psql, 14, cr.getPurpose());
        bindString(psql, 15, cr.getCopyright());
        bindString(psql, 16, cr.getCopyrightLabel());
        psql.setBytes(17, json);
        psql.executeUpdate();    
        if (cr instanceof CodeSystem) {
          codesystems.add((CodeSystem) cr);
        } else if (cr instanceof ConceptMap) {
          mappings.add((ConceptMap) cr);
        }
        cr.setUserData("db.key", lastResKey);
      } 
    } catch (SQLException e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
    }
    time(start);
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
          psql.setInt(2, ((Integer) cs.getUserData("db.key")).intValue());
          bindString(psql, 3, p.getCode());
          bindString(psql, 4, p.getUri());
          bindString(psql, 5, p.getDescription());
          bindString(psql, 6, p.getType().toCode());  
          psql.executeUpdate();     
          p.setUserData("db.key", lastPropKey);   
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
              psql.setInt(2, ((Integer) cm.getUserData("db.key")).intValue());
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
    // TODO Auto-generated method stub

    psql.setInt(1, ++lastVSKey);
    psql.setInt(2, ((Integer) vs.getUserData("db.key")).intValue());
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

  private void addConcepts(CodeSystem cs, List<ConceptDefinitionComponent> list, PreparedStatement psql, int parent) throws SQLException {
    for (ConceptDefinitionComponent cd : list) {
      psql.setInt(1, ++lastConceptKey);
      psql.setInt(2, ((Integer) cs.getUserData("db.key")).intValue());
      if (parent == 0) {
        psql.setNull(3, java.sql.Types.INTEGER);
      } else {
        psql.setInt(3, parent);
      }
      bindString(psql, 4, cd.getCode());
      bindString(psql, 5, cd.getDisplay());
      bindString(psql, 6, cd.getDefinition());
      psql.executeUpdate();    
      cd.setUserData("db.key", lastConceptKey);   
      addConcepts(cs, cd.getConcept(), psql, lastConceptKey);
    }
  }

  private void addConceptProperties(CodeSystem cs, List<ConceptDefinitionComponent> list, PreparedStatement psql) throws SQLException {
    for (ConceptDefinitionComponent cd : list) {
      for (ConceptPropertyComponent p : cd.getProperty()) { 
        psql.setInt(1, ++lastCPropKey);
        psql.setInt(2, ((Integer) cs.getUserData("db.key")).intValue());
        psql.setInt(3, ((Integer) cd.getUserData("db.key")).intValue());
        PropertyComponent pd = getPropDefn(p.getCode(), cs);
        if (pd == null) {
          psql.setNull(4, java.sql.Types.INTEGER);
        } else {
          psql.setInt(4, ((Integer) pd.getUserData("db.key")).intValue());
        }
        bindString(psql, 5, p.getCode());
        bindString(psql, 6, p.getValue().primitiveValue());
        psql.executeUpdate();    
        p.setUserData("db.key", lastCPropKey);   
      }
      addConceptProperties(cs, cd.getConcept(), psql);
    }
  }

  private void addConceptDesignations(CodeSystem cs, List<ConceptDefinitionComponent> list, PreparedStatement psql) throws SQLException {
    for (ConceptDefinitionComponent cd : list) {
      for (ConceptDefinitionDesignationComponent p : cd.getDesignation()) { 
        psql.setInt(1, ++lastDesgKey);
        psql.setInt(2, ((Integer) cs.getUserData("db.key")).intValue());
        psql.setInt(3, ((Integer) cd.getUserData("db.key")).intValue());        
        bindString(psql, 4, p.getUse().getSystem());
        bindString(psql, 5, p.getUse().getCode());
        bindString(psql, 6, p.getLanguage());
        bindString(psql, 7, p.getValue());
        psql.executeUpdate();    
        p.setUserData("db.key", lastDesgKey);   
      }
      addConceptDesignations(cs, cd.getConcept(), psql);
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
        "UseSystem    varchar NOT NULL,\r\n"+
        "UseCode      varchar NOT NULL,\r\n"+
        "Lang         varchar NOT NULL,\r\n"+
        "Value        text NOT NULL,\r\n"+
        "PRIMARY KEY (Key))\r\n");
  }

  private void makeMappingsTable(Connection con) throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("CREATE TABLE ConceptMappings (\r\n"+
        "Key           integer NOT NULL,\r\n"+
        "ResourceKey   integer NOT NULL,\r\n"+
        "SourceSystem  varchar NOT NULL,\r\n"+
        "SourceVersion varchar NOT NULL,\r\n"+
        "SourceCode    varchar NOT NULL,\r\n"+
        "Relationship  varchar NOT NULL,\r\n"+
        "TargetSystem  varchar NOT NULL,\r\n"+
        "TargetVersion varchar NOT NULL,\r\n"+
        "TargetCode    varchar NOT NULL,\r\n"+
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

      sql = sql.trim();

      String clss = "grid";
      List<RenderingRule> rules = new ArrayList<>();
      if (sql.startsWith("fmt:")) {
        String fmt = sql.substring(0, sql.indexOf(" " ));
        sql = sql.substring(fmt.length()).trim();
        clss = readFormatRules(rules, fmt);
      }
      for (int i = 0; i < rules.size(); i++) {
        RenderingRule rule = rules.get(i);
        if (rule.linksCol != 0 && rule.linksCol <= rules.size()) {
          rules.get(rule.linksCol -1).linkCol = i+1;
        }
      }
      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery(sql);
      ResultSetMetaData rsmd = rs.getMetaData();
      XhtmlNode tbl = new XhtmlNode(NodeType.Element, "table").attribute("class", clss);

      XhtmlNode tr = tbl.tr();
      for (int i = 1; i <= rsmd.getColumnCount(); i++) {
        RenderingRule rule = i > rules.size() ? new RenderingRule(RenderingType.AutoDetect) : rules.get(i-1);
        if (rule.type != RenderingType.None) {
          tr.td().style("background-color: #eeeeee").tx(rsmd.getColumnName(i));
        }
      }

      while (rs.next()) {
        tr = tbl.tr();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
          RenderingRule rule = i > rules.size() ? new RenderingRule(RenderingType.AutoDetect) : rules.get(i-1);
          String s = rs.getString(i);
          switch (rule.type) {
          case AutoDetect:
            if (Utilities.isAbsoluteUrlLinkable(s)) {
              tr.td().ah(s).tx(s);
            } else if (md.isProbablyMarkdown(s, true)) {
              tr.td().markdown(s, "sql");
            } else if (rule.linkCol > 0) {
              tr.td().ah(rs.getString(rule.linkCol)).tx(s);
            } else {
              tr.td().tx(s);
            }
            break;
          case Markdown:
            tr.td().markdown(s, "sql");
            break;
          case None:
            break;
          case String:
            if (rule.linkCol > 0) {
              tr.td().ah(rs.getString(rule.linkCol)).tx(s);
            } else {
              tr.td().tx(s);
            }
            break;
          default:
            tr.td().tx(s);
            break;
          }
        }
      }
      time(start);
      return new XhtmlComposer(true, false).compose(tbl);
    } catch (Exception e) {
      errors.add(e.getMessage());
      if (debug) {
        e.printStackTrace();
      }
      time(start);
      return "<span style=\"color: maroon\">Error processing SQL: "+Utilities.escapeXml(e.getMessage())+"</span>";
    }
  }

  private String readFormatRules(List<RenderingRule> rules, String fmt) {
    // fmt:clss(;col) 
    // where col is one of:
    //   a = autodetect - default
    //   s = string - don't autodetect
    //   md = it's markdown
    //   l:x = it's a link for column x
    //
    String[] p = fmt.substring(4).split("\\;");
    for (int i = 1; i < p.length; i++) {
      RenderingRule rule = null;
      if (Utilities.noString(p[i]) || "a".equals(p[i])) {
        rule = new RenderingRule(RenderingType.AutoDetect);
      } else if ("md".equals(p[i])) {
        rule = new RenderingRule(RenderingType.Markdown);
      } else if ("s".equals(p[i])) {
        rule = new RenderingRule(RenderingType.String);
      } else if (p[i].startsWith("l:")) {
        rule = new RenderingRule(RenderingType.None);
        rule.linksCol = Integer.parseInt(p[i].substring(2));
      } else {
        throw new FHIRException("Unable to read fmt "+fmt+" at index "+i);
      }
      rules.add(rule);
    }
    return Utilities.noString(p[0]) ? "grid" : p[0];
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



}
