package org.folio.rest.support;

import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

public final class CqlQuery {
  private final String cql;

  public CqlQuery(String cql) {
    this.cql = cql;
  }

  /**
   * True if {@code cqlQuery} is a CQL expression with {@code cql.allRecords}.
   */
  public boolean isMatchingAll() {
    CQLNode cqlNode;
    try {
      cqlNode = new CQLParser().parse(cql);
    } catch (Exception e) {
      return false;
    }
    if (!(cqlNode instanceof CQLTermNode)) {
      return false;
    }
    var node = (CQLTermNode) cqlNode;
    // cql.allRecords: A special index which matches every record available. Every record is matched no matter what
    // values are provided for the relation and term, but the recommended syntax is: cql.allRecords = 1
    // http://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part5-cql/searchRetrieve-v1.0-os-part5-cql.html#_Toc324166821
    return "cql.allRecords".equalsIgnoreCase(node.getIndex());
  }
}
