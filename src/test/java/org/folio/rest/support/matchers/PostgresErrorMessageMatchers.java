package org.folio.rest.support.matchers;

import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * Matchers that use the SQLSTATE error codes
 * <a href="https://www.postgresql.org/docs/current/errcodes-appendix.html">https://www.postgresql.org/docs/current/errcodes-appendix.html</a>.
 * <p>
 * SQLSTATE error codes are stable, whereas the error description may change when switching the PostgreSQL version,
 * and the order of the error elements may change.
 */
public class PostgresErrorMessageMatchers {
  private PostgresErrorMessageMatchers() { }

  public static Matcher<String> isMaximumSequenceValueError(final String sequenceName) {
    // 2200H = sequence_generator_limit_exceeded
    // vertx-pg-client does not return code in message (ONLY message)
    return allOf(containsString("\"" + sequenceName + "\""));
  }

  public static Matcher<String> isUniqueViolation(final String uniqueIndexName) {
    // 23505 = unique_violation
    return allOf(containsString("\"" + uniqueIndexName + "\""));
  }
}
