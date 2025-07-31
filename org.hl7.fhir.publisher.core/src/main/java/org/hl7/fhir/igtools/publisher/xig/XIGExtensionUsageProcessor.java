package org.hl7.fhir.igtools.publisher.xig;

import org.hl7.fhir.utilities.Utilities;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class XIGExtensionUsageProcessor {
  public static Set<String> used = new HashSet<>();

  protected static class BaseVisitor {
    private final int userKey;
    private final Map<String, Integer> definitions;
    private final PreparedStatement psql;
    private final PreparedStatement psql2;

    public BaseVisitor(int userKey, Map<String, Integer> definitions, PreparedStatement psql, PreparedStatement psql2) {
      this.userKey = userKey;
      this.definitions = definitions;
      this.psql = psql;
      this.psql2 = psql2;
    }

    protected void seeExtension(String url) {
      if (Utilities.isAbsoluteUrl(url)) {
        try {
          int ukey;
          if (definitions.containsKey(url)) {
            ukey = definitions.get(url);
          } else {
            ukey = definitions.size() + 1;
            definitions.put(url, ukey);

            psql.setInt(1, ukey);
            psql.setString(2, url);
            psql.execute();

          }
          String k = "" + ukey + ":" + userKey;
          if (!used.contains(k)) {
            used.add(k);
            psql2.setInt(1, ukey);
            psql2.setInt(2, userKey);
            psql2.execute();
          }
        } catch (SQLException e) {
          throw new Error(e);
        }
      }
    }
  }

  public static class ExtensionVisitorR5 extends BaseVisitor implements org.hl7.fhir.r5.utils.ElementVisitor.IElementVisitor {
    public ExtensionVisitorR5(int userKey, Map<String, Integer> definitions, PreparedStatement psql, PreparedStatement psql2) {
      super(userKey, definitions, psql, psql2);
    }

    @Override
    public org.hl7.fhir.r5.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.r5.model.Resource resource) {
      if (resource instanceof org.hl7.fhir.r5.model.DomainResource) {
        org.hl7.fhir.r5.model.DomainResource dr = (org.hl7.fhir.r5.model.DomainResource) resource;
        for (org.hl7.fhir.r5.model.Extension ex : dr.getExtension()) {
          seeExtension(ex.getUrl());
        }
        for (org.hl7.fhir.r5.model.Extension ex : dr.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.r5.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }

    @Override
    public org.hl7.fhir.r5.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.r5.model.Element element) {
      for (org.hl7.fhir.r5.model.Extension ex : element.getExtension()) {
        seeExtension(ex.getUrl());
      }
      if (element instanceof org.hl7.fhir.r5.model.BackboneElement) {
        org.hl7.fhir.r5.model.BackboneElement be = (org.hl7.fhir.r5.model.BackboneElement) element;
        for (org.hl7.fhir.r5.model.Extension ex : be.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      if (element instanceof org.hl7.fhir.r5.model.BackboneType) {
        org.hl7.fhir.r5.model.BackboneType be = (org.hl7.fhir.r5.model.BackboneType) element;
        for (org.hl7.fhir.r5.model.Extension ex : be.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.r5.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }
  }

  public static class ExtensionVisitorR4 extends BaseVisitor implements org.hl7.fhir.r4.utils.ElementVisitor.IElementVisitor {
    public ExtensionVisitorR4(int userKey, Map<String, Integer> definitions, PreparedStatement psql, PreparedStatement psql2) {
      super(userKey, definitions, psql, psql2);
    }

    @Override
    public org.hl7.fhir.r4.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.r4.model.Resource resource) {
      if (resource instanceof org.hl7.fhir.r4.model.DomainResource) {
        org.hl7.fhir.r4.model.DomainResource dr = (org.hl7.fhir.r4.model.DomainResource) resource;
        for (org.hl7.fhir.r4.model.Extension ex : dr.getExtension()) {
          seeExtension(ex.getUrl());
        }
        for (org.hl7.fhir.r4.model.Extension ex : dr.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.r4.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }

    @Override
    public org.hl7.fhir.r4.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.r4.model.Element element) {
      for (org.hl7.fhir.r4.model.Extension ex : element.getExtension()) {
        seeExtension(ex.getUrl());
      }
      if (element instanceof org.hl7.fhir.r4.model.BackboneElement) {
        org.hl7.fhir.r4.model.BackboneElement be = (org.hl7.fhir.r4.model.BackboneElement) element;
        for (org.hl7.fhir.r4.model.Extension ex : be.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      if (element instanceof org.hl7.fhir.r4.model.BackboneType) {
        org.hl7.fhir.r4.model.BackboneType be = (org.hl7.fhir.r4.model.BackboneType) element;
        for (org.hl7.fhir.r4.model.Extension ex : be.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.r4.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }
  }

  public static class ExtensionVisitorR3 extends BaseVisitor implements org.hl7.fhir.dstu3.utils.ElementVisitor.IElementVisitor {
    public ExtensionVisitorR3(int userKey, Map<String, Integer> definitions, PreparedStatement psql, PreparedStatement psql2) {
      super(userKey, definitions, psql, psql2);
    }

    @Override
    public org.hl7.fhir.dstu3.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.dstu3.model.Resource resource) {
      if (resource instanceof org.hl7.fhir.dstu3.model.DomainResource) {
        org.hl7.fhir.dstu3.model.DomainResource dr = (org.hl7.fhir.dstu3.model.DomainResource) resource;
        for (org.hl7.fhir.dstu3.model.Extension ex : dr.getExtension()) {
          seeExtension(ex.getUrl());
        }
        for (org.hl7.fhir.dstu3.model.Extension ex : dr.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.dstu3.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }

    @Override
    public org.hl7.fhir.dstu3.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.dstu3.model.Element element) {
      for (org.hl7.fhir.dstu3.model.Extension ex : element.getExtension()) {
        seeExtension(ex.getUrl());
      }
      if (element instanceof org.hl7.fhir.dstu3.model.BackboneElement) {
        org.hl7.fhir.dstu3.model.BackboneElement be = (org.hl7.fhir.dstu3.model.BackboneElement) element;
        for (org.hl7.fhir.dstu3.model.Extension ex : be.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.dstu3.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }
  }
  public static class ExtensionVisitorR4B extends BaseVisitor implements org.hl7.fhir.r4b.utils.ElementVisitor.IElementVisitor {
    public ExtensionVisitorR4B(int userKey, Map<String, Integer> definitions, PreparedStatement psql, PreparedStatement psql2) {
      super(userKey, definitions, psql, psql2);
    }

    @Override
    public org.hl7.fhir.r4b.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.r4b.model.Resource resource) {
      if (resource instanceof org.hl7.fhir.r4b.model.DomainResource) {
        org.hl7.fhir.r4b.model.DomainResource dr = (org.hl7.fhir.r4b.model.DomainResource) resource;
        for (org.hl7.fhir.r4b.model.Extension ex : dr.getExtension()) {
          seeExtension(ex.getUrl());
        }
        for (org.hl7.fhir.r4b.model.Extension ex : dr.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.r4b.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }

    @Override
    public org.hl7.fhir.r4b.utils.ElementVisitor.ElementVisitorInstruction visit(Object context, org.hl7.fhir.r4b.model.Element element) {
      for (org.hl7.fhir.r4b.model.Extension ex : element.getExtension()) {
        seeExtension(ex.getUrl());
      }
      if (element instanceof org.hl7.fhir.r4b.model.BackboneElement) {
        org.hl7.fhir.r4b.model.BackboneElement be = (org.hl7.fhir.r4b.model.BackboneElement) element;
        for (org.hl7.fhir.r4b.model.Extension ex : be.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      if (element instanceof org.hl7.fhir.r4b.model.BackboneType) {
        org.hl7.fhir.r4b.model.BackboneType be = (org.hl7.fhir.r4b.model.BackboneType) element;
        for (org.hl7.fhir.r4b.model.Extension ex : be.getModifierExtension()) {
          seeExtension(ex.getUrl());
        }
      }
      return org.hl7.fhir.r4b.utils.ElementVisitor.ElementVisitorInstruction.VISIT_CHILDREN;
    }
  }

}
