package org.folio.rest.support.matchers;

import java.util.Objects;

import org.folio.rest.support.Response;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResponseMatcher {

  public static Matcher<Response> hasValidationError(
    String expectedMessage, String expectedKey, String expectedValue) {

    return new TypeSafeMatcher<Response>() {
      @Override
      protected boolean matchesSafely(Response response) {
        if (response.getStatusCode() != 422) {
          return false;
        }

        try {
          JsonArray errors = response.getJson().getJsonArray("errors");
          if (errors != null && errors.size() == 1) {
            JsonObject error = errors.getJsonObject(0);
            JsonArray parameters = error.getJsonArray("parameters");

            if (parameters != null && parameters.size() == 1) {
              String message = error.getString("message");
              String key = parameters.getJsonObject(0).getString("key");
              String value = parameters.getJsonObject(0).getString("value");

              return Objects.equals(expectedMessage, message)
                && Objects.equals(expectedKey, key)
                && Objects.equals(expectedValue, value);
            }
          }

          return false;
        } catch (DecodeException ex) {
          return false;
        }
      }

      @Override
      public void describeTo(Description description) {
        description
          .appendText("Response has 422 status and 'message' - ").appendValue(expectedMessage)
          .appendText(", 'key' - ").appendValue(expectedKey)
          .appendText(" and 'value' - ").appendValue(expectedValue);
      }
    };
  }
}
