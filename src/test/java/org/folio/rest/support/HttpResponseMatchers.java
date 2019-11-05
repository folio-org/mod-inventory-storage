package org.folio.rest.support;

import org.folio.HttpStatus;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.vertx.core.json.JsonObject;

public class HttpResponseMatchers {
  public static Matcher<Response> statusCodeIs(int statusCode) {
    return new TypeSafeDiagnosingMatcher<Response>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(
          String.format("an response with status code %s", statusCode));
      }

      @Override
      protected boolean matchesSafely(Response response, Description description) {
        boolean matches = response.getStatusCode() == statusCode;

        if(!matches) {
          description.appendText(String.format("Response: %s",
            response.toString()));
        }

        return matches;
      }
    };
  }

  public static Matcher<Response> statusCodeIs(HttpStatus httpStatus) {
    return statusCodeIs(httpStatus.toInt());
  }

  public static Matcher<Response> errorMessageContains(String substring) {
    return new TypeSafeDiagnosingMatcher<Response>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("an response where the body is a JSON where errors[0].message contains '")
          .appendText(substring).appendText("'");
      }

      @Override
      protected boolean matchesSafely(Response response, Description description) {
        JsonObject jsonObject;
        try {
          jsonObject = response.getJson();
        } catch (Exception e) {
          description.appendText("Response where body has no or invalid JSON (" + e.getMessage() + "): " + response.getBody());
          return false;
        }
        String message;
        try {
          message = jsonObject.getJsonArray("errors").getJsonObject(0).getString("message").toString();
        } catch (Exception e) {
          description.appendText("Response JSON body doesn't have errors[0].message element: " + jsonObject.encodePrettily());
          return false;
        }
        if (message.contains(substring)) {
          return true;
        }
        description.appendText("Response: " + jsonObject.encodePrettily());
        return false;
      }
    };
  }
}
