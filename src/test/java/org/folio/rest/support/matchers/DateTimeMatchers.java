package org.folio.rest.support.matchers;

import java.util.Arrays;
import java.util.List;

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
    List<String> acceptableFormats = Arrays.asList(
      "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      "yyyy-MM-dd'T'HH:mm:ss.SSS+0000"
    );

    return new TypeSafeMatcher<String>() {
      @Override
      protected boolean matchesSafely(String dateTimeAsString) {
        return acceptableFormats.stream()
          .anyMatch(dateTimeFormat -> {
            try {
              DateTimeFormat.forPattern(dateTimeFormat)
                .parseDateTime(dateTimeAsString);
            } catch (IllegalArgumentException ex) {
              return false;
            }

            return true;
          });
      }

      @Override
      public void describeTo(Description description) {
        description
          .appendText("Has an ISO-8601 format:")
          .appendValue(String.join("; ", acceptableFormats));
      }
    };
  }
}
