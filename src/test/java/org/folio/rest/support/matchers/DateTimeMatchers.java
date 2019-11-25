package org.folio.rest.support.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormat;

public final class DateTimeMatchers {

  private DateTimeMatchers() {
    throw new UnsupportedOperationException("Do no instantiate");
  }

  private static Matcher<String> withinSecondsBefore(
    Seconds seconds, DateTime beforeDateTime) {

    return new TypeSafeMatcher<String>() {

      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time within [%s] seconds before [%s]",
          seconds.getSeconds(), beforeDateTime.toString()));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        DateTime actual = DateTime.parse(textRepresentation);

        return actual.isBefore(beforeDateTime)
          && Seconds.secondsBetween(beforeDateTime, actual).isLessThan(seconds);
      }
    };
  }

  public static Matcher<String> withinSecondsBeforeNow(Seconds seconds) {
    return withinSecondsBefore(seconds, DateTime.now(DateTimeZone.UTC));
  }

  public static Matcher<String> hasIsoFormat() {
    String acceptableFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    return new TypeSafeMatcher<String>() {
      @Override
      protected boolean matchesSafely(String dateTimeAsString) {
        try {
          DateTimeFormat.forPattern(acceptableFormat)
            .parseDateTime(dateTimeAsString);
        } catch (IllegalArgumentException ex) {
          return false;
        }

        return true;
      }

      @Override
      public void describeTo(Description description) {
        description
          .appendText("Has ISO-8601 format:")
          .appendValue(acceptableFormat);
      }
    };
  }
}
