package org.folio.rest.support;

import org.apache.commons.lang3.StringUtils;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

public final class CqlQuery {
  private final String cqlQuery;

  public CqlQuery(String cqlQuery) {
    this.cqlQuery = cqlQuery;
  }

  /**
   * True if {@code cqlQuery} is a CQL expression that is known to always match all records, for
   * example {@code id==*} or {@code cql.allRecords=1}.
   * If false is returned the expression may still match all records because this
   * method covers only a few cases and doesn't consider the existing data.
   */
  public boolean isMatchingAll() {
    CQLNode cqlNode;
    try {
      cqlNode = new CQLParser().parse(cqlQuery);
    } catch (Exception e) {
      return false;
    }
    if (! (cqlNode instanceof CQLTermNode)) {
      return false;
    }
    var node = (CQLTermNode) cqlNode;
    // cql.allRecords: A special index which matches every record available. Every record is matched no matter what
    // values are provided for the relation and term, but the recommended syntax is: cql.allRecords = 1
    // http://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part5-cql/searchRetrieve-v1.0-os-part5-cql.html#_Toc324166821
    if ("cql.allRecords".equalsIgnoreCase(node.getIndex())) {
      return true;
    }
    var base = node.getRelation() == null ? null : node.getRelation().getBase();
    // In RMB id=* matches all records: https://github.com/folio-org/raml-module-builder#cql-matching-all-records
    return "id".equalsIgnoreCase(node.getIndex())
        && StringUtils.equalsAny(base, "=", "==")
        && "*".equals(node.getTerm());
  }
}
