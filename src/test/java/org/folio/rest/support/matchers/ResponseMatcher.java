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

    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Response has 422 status and error message parameter 'value' - ").appendValue(expectedValue);
      }

      @Override
      protected boolean matchesSafely(Response response) {
        try {
          return matchesParameterValue(response, expectedValue);
        } catch (DecodeException ex) {
          return false;
        }
      }
    };
  }

  public static Matcher<Response> hasValidationError(
    String expectedMessage, String expectedKey, String expectedValue) {

    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("Response has 422 status and 'message' - ").appendValue(expectedMessage)
          .appendText(", 'key' - ").appendValue(expectedKey)
          .appendText(" and 'value' - ").appendValue(expectedValue);
      }

      @Override
      protected boolean matchesSafely(Response response) {
        try {
          return matchesMessageKeyAndValue(response, expectedMessage, expectedKey, expectedValue);
        } catch (DecodeException ex) {
          return false;
        }
      }
    };
  }

  private static boolean matchesParameterValue(Response response, String expectedValue) {
    if (response.getStatusCode() != 422) {
      return false;
    }

    Errors errors = response.getJson().mapTo(Errors.class);
    if (errors.getErrors().size() != 1) {
      return false;
    }
    final Error error = errors.getErrors().getFirst();
    if (error.getParameters() == null || error.getParameters().size() != 1) {
      return false;
    }
    final Parameter parameter = error.getParameters().getFirst();
    return Objects.equals(expectedValue, parameter.getValue());
  }

  private static boolean matchesMessageKeyAndValue(
    Response response, String expectedMessage, String expectedKey, String expectedValue) {
    if (response.getStatusCode() != 422) {
      return false;
    }

    Errors errors = response.getJson().mapTo(Errors.class);
    if (errors.getErrors().size() != 1) {
      return false;
    }
    final Error error = errors.getErrors().getFirst();

    if (error.getParameters() == null || error.getParameters().size() != 1) {
      return false;
    }
    final Parameter parameter = error.getParameters().getFirst();

    return Objects.equals(expectedMessage, error.getMessage())
           && Objects.equals(expectedKey, parameter.getKey())
           && Objects.equals(expectedValue, parameter.getValue());
  }
}

