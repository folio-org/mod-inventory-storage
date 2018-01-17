package org.folio.rest.support;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class HttpResponseMatchers {
  public static Matcher<Response> statusCodeIs(int statusCode) {
    return new TypeSafeDiagnosingMatcher<Response>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(
          String.format("an response with status code %s", statusCode));
      }

      @Override
      protected boolean matchesSafely(Response Response, Description description) {
        boolean matches = Response.getStatusCode() == statusCode;

        if(!matches) {
          description.appendText(String.format("Response: %s",
            Response.toString()));
        }

        return matches;
      }
    };
  }
}
