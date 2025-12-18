package org.folio.rest.support.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import org.hamcrest.Matcher;

/**
 * Matchers that use the SQLSTATE error codes
 * <a href="https://www.postgresql.org/docs/current/errcodes-appendix.html">https://www.postgresql.org/docs/current/errcodes-appendix.html</a>.
 * SQLSTATE error codes are stable, whereas the error description may change when switching the PostgreSQL version,
 * and the order of the error elements may change.
 */
public final class PostgresErrorMessageMatchers {
  private PostgresErrorMessageMatchers() { }

  public static Matcher<String> isMaximumSequenceValueError(final String sequenceName) {
    // 2200H = sequence_generator_limit_exceeded
    return allOf(containsString("2200H"), containsString(String.format("\"%s\"", sequenceName)));
  }

  public static Matcher<String> isUniqueViolation(final String uniqueIndexName) {
    // 23505 = unique_violation
    return allOf(containsString("23505"), containsString(String.format("\"%s\"", uniqueIndexName)));
  }
}
