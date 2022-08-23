package org.folio.rest.support;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;

public class JsonObjectMatchers {
  public static Matcher<JsonObject> identifierMatches(String identifierTypeId, String value) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "an identifier with identifierTypeId: %s and value: %s", identifierTypeId, value));
      }

      @Override
      protected boolean matchesSafely(JsonObject entry) {
        return entry.getString("identifierTypeId").equals(identifierTypeId)
          && entry.getString("value").equals(value);
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

  public static Matcher<List<JsonObject>> hasSoleMessageContaining(String message) {
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

  public static Matcher<JsonObject> equalsIgnoringMetadata(JsonObject expected) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(
          "a JsonObject being equal when ignoring metadata property: " + expected);
      }

      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        var finalJsonObject = jsonObject.copy();
        var finalExpected = expected.copy();
        finalJsonObject.remove("metadata");
        finalExpected.remove("metadata");
        return finalJsonObject.equals(finalExpected);
      }
    };
  }
}
