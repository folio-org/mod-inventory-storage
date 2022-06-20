package org.folio.rest.support;

import java.util.regex.Pattern;

public final class CqlQuery {
  private static final Pattern CQL_MATCHES_ALL = Pattern.compile(
      "^ *id *==? *\\* *$|^ *id *==? *\"\\*\" *$|^ *cql.allRecords *= *1 *$");

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
    return CQL_MATCHES_ALL.matcher(cqlQuery).find();
  }
}
