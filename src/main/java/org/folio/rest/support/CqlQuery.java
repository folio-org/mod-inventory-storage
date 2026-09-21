package org.folio.rest.support;

import java.util.Optional;
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
    if (!(cqlNode instanceof CQLTermNode node)) {
      return false;
    }
    // cql.allRecords: A special index which matches every record available. Every record is matched no matter what
    // values are provided for the relation and term, but the recommended syntax is: cql.allRecords = 1
    // http://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part5-cql/searchRetrieve-v1.0-os-part5-cql.html#_Toc324166821
    return "cql.allRecords".equalsIgnoreCase(node.getIndex());
  }

  /**
   * If this query is a single exact-match ({@code ==}) term against {@code indexName}, returns its term value.
   * Empty for any other query shape (boolean combinations, other relations, other indexes, unparsable queries).
   *
   * <p>Note: a compound query such as {@code indexName==value and other==term} also returns empty. This is an
   * accepted limitation: only bare single-term exact-match queries will return the term value.
   */
  public Optional<String> exactMatchTerm(String indexName) {
    CQLNode cqlNode;
    try {
      cqlNode = new CQLParser().parse(cql);
    } catch (Exception e) {
      return Optional.empty();
    }
    if (!(cqlNode instanceof CQLTermNode node)
        || !indexName.equalsIgnoreCase(node.getIndex())
        || !"==".equals(node.getRelation().getBase())) {
      return Optional.empty();
    }
    return Optional.of(node.getTerm());
  }
}
