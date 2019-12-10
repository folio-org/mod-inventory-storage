package org.folio.rest.support.matchers;

import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.containsString;

public class PostgresErrorMessageMatchers {
  private PostgresErrorMessageMatchers() { }

  public static Matcher<String> isMaximumSequenceValueError(final String sequenceName) {
    return containsString(
      "ErrorMessage(fields=[(Severity, ERROR), (V, ERROR), (SQLSTATE, 2200H), " +
        "(Message, nextval: reached maximum value of sequence \""
        + sequenceName + "\"");
  }
}
