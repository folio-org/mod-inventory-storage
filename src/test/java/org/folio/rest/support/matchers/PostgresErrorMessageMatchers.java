package org.folio.rest.support.matchers;

import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;

import static org.hamcrest.CoreMatchers.containsString;

public class PostgresErrorMessageMatchers {
  private PostgresErrorMessageMatchers() { }

  @NotNull
  public static Matcher<String> isMaximumSequenceValueError() {
    return containsString(
      "ErrorMessage(fields=[(Severity, ERROR), (V, ERROR), (SQLSTATE, 2200H), " +
        "(Message, nextval: reached maximum value of sequence \"hrid_instances_seq\"");
  }
}
