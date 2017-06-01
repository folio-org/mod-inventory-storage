package org.folio.rest.support;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class HttpResponseMatchers {
  public static Matcher statusCodeIs(int statusCode) {
    return new TypeSafeDiagnosingMatcher<TextResponse>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(
          String.format("an response with status code %s", statusCode));
      }

      @Override
      protected boolean matchesSafely(TextResponse textResponse, Description description) {
        boolean matches = textResponse.getStatusCode() == statusCode;

        if(!matches) {
          description.appendText(String.format("Response: %s",
            textResponse.toString()));
        }

        return matches;
      }
    };
  }
}
