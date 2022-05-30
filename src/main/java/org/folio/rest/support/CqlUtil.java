package org.folio.rest.support;

import java.util.regex.Pattern;

public final class CqlUtil {
  private static final Pattern CQL_MATCHES_ALL = Pattern.compile(
      "^ *id *==? *\\* *$|^ *id *==? *\"\\*\" *$|^ *cql.allRecords *= *1 *$");

  private CqlUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  /**
   * True if cql is a CQL expression that is known to always match all records, for
   * example {@code id==*} or {@code cql.allRecords=1}.
   * If false is returned the expression may still match all records because this
   * method covers only a few cases and doesn't consider the existing data.
   */
  public static boolean isMatchingAll(String cql) {
    return CQL_MATCHES_ALL.matcher(cql).find();
  }
}
