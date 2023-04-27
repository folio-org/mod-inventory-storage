package org.folio.rest.support.matchers;

import io.vertx.core.json.DecodeException;
import java.util.Objects;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.support.Response;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ResponseMatcher {

  public static Matcher<Response> hasValidationError(String expectedValue) {

    return new TypeSafeMatcher<Response>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Response has 422 status and error message parameter 'value' - ").appendValue(expectedValue);
      }

      @Override
      protected boolean matchesSafely(Response response) {
        try {
          if (response.getStatusCode() != 422) {
            return false;
          }

          Errors errors = response.getJson().mapTo(Errors.class);
          if (errors.getErrors().size() != 1) {
            return false;
          }
          final Error error = errors.getErrors().get(0);
          if (error.getParameters() == null || error.getParameters().size() != 1) {
            return false;
          }
          final Parameter parameter = error.getParameters().get(0);
          return Objects.equals(expectedValue, parameter.getValue());
        } catch (DecodeException ex) {
          return false;
        }
      }
    };
  }

  public static Matcher<Response> hasValidationError(
    String expectedMessage, String expectedKey, String expectedValue) {

    return new TypeSafeMatcher<Response>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Response has 422 status and 'message' - ").appendValue(expectedMessage)
          .appendText(", 'key' - ").appendValue(expectedKey)
          .appendText(" and 'value' - ").appendValue(expectedValue);
      }

      @Override
      protected boolean matchesSafely(Response response) {
        if (response.getStatusCode() != 422) {
          return false;
        }

        try {
          Errors errors = response.getJson().mapTo(Errors.class);
          if (errors.getErrors().size() == 1) {
            final Error error = errors.getErrors().get(0);

            if (error.getParameters() != null && error.getParameters().size() == 1) {
              final Parameter parameter = error.getParameters().get(0);

              return Objects.equals(expectedMessage, error.getMessage())
                && Objects.equals(expectedKey, parameter.getKey())
                && Objects.equals(expectedValue, parameter.getValue());
            }
          }

          return false;
        } catch (DecodeException ex) {
          return false;
        }
      }
    };
  }
}
