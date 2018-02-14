package org.folio.rest.support;

import io.vertx.core.json.JsonObject;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Matcher for a io.vertx.core.json.JsonObject.
 */
public class JsonObjectMatchers {
  private JsonObjectMatchers() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  public static Matcher<JsonObject> contributorMatches(String contributorNameTypeId, String name) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a contributor with contributorNameTypeId \"%s\" and name \"%s\"", contributorNameTypeId, name));
      }

      @Override
      protected boolean matchesSafely(JsonObject entry, Description mismatchDescription) {
        if (   StringUtils.equals(entry.getString("contributorNameTypeId"), contributorNameTypeId)
            && StringUtils.equals(entry.getString("name"), name)) {
          return true;
        }
        mismatchDescription.appendText("contributor was " + entry.encodePrettily());
        return false;
      }
    };
  }

  public static Matcher<JsonObject> identifierMatches(String identifierTypeId, String value) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "an identifier with identifierTypeId \"%s\" and value \"%s\"", identifierTypeId, value));
      }

      @Override
      protected boolean matchesSafely(JsonObject entry, Description mismatchDescription) {
        if (   StringUtils.equals(entry.getString("identifierTypeId"), identifierTypeId)
            && StringUtils.equals(entry.getString("value"), value)) {
          return true;
        }
        mismatchDescription.appendText("identifier was " + entry.encodePrettily());
        return false;
      }
    };
  }

  public static Matcher<JsonObject> validationErrorMatches(String message, String property) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a validation error message: %s for property: %s", message, property));
      }

      @Override
      protected boolean matchesSafely(JsonObject entry) {
        List<JsonObject> parameters = JsonArrayHelper.toList(
          entry.getJsonArray("parameters"));

        return entry.getString("message").equals(message) &&
          parameters.stream().anyMatch(p -> p.getString("key").equals(property));
      }
    };
  }

  public static Matcher<List<JsonObject>> hasSoleMessgeContaining(String message) {
    return new TypeSafeMatcher<List<JsonObject>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a sole validation error message containing: %s", message));
      }

      @Override
      protected boolean matchesSafely(List<JsonObject> errors) {
        if(errors.size() == 1) {
          return errors.get(0).getString("message").contains(message);
        }
        else
          return false;
        }
    };
  }
}
