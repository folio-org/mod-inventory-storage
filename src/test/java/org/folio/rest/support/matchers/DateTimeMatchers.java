package org.folio.rest.support.matchers;

import java.time.Instant;
import java.util.function.Function;
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

  private static <T> Matcher<T> withinSecondsBefore(
    Seconds seconds, DateTime beforeDateTime,
    Function<T, DateTime> jsonToDateTimeConverter) {

    return new TypeSafeMatcher<T>() {

      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time within [%s] seconds before [%s]",
          seconds.getSeconds(), beforeDateTime.toString()));
      }

      @Override
      protected boolean matchesSafely(T actualValue) {
        DateTime actualDateTime = jsonToDateTimeConverter.apply(actualValue);

        return actualDateTime.isBefore(beforeDateTime)
          && Seconds.secondsBetween(beforeDateTime, actualDateTime).isLessThan(seconds);
      }
    };
  }

  public static Matcher<Instant> withinSecondsBeforeNow(Seconds seconds) {
    return withinSecondsBefore(seconds,
      DateTime.now(DateTimeZone.UTC),
      (Instant instant) -> new DateTime(instant.toEpochMilli(), DateTimeZone.UTC)
    );
  }

  public static Matcher<String> withinSecondsBeforeNowAsString(Seconds seconds) {
    return withinSecondsBefore(seconds, DateTime.now(DateTimeZone.UTC), DateTime::parse);
  }

  public static Matcher<String> hasIsoFormat() {
    String acceptableFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    return new TypeSafeMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Has ISO-8601 format:")
          .appendValue(acceptableFormat);
      }

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
    };
  }
}
