package org.folio.rest.support.matchers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public final class OaiPmhResponseMatchers {

  static JsonPointer itemFieldsPointer = JsonPointer.from("/itemsandholdingsfields/items");

  private OaiPmhResponseMatchers() {
  }

  public static Matcher<JsonObject> isDeleted() {

    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Is deleted ")
          .appendValue(true);
      }

      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        final Object deleted = JsonPointer.from("/deleted")
          .queryJson(jsonObject);
        return Boolean.parseBoolean(deleted.toString());
      }
    };
  }

  public static Matcher<JsonObject> hasCallNumber(String... callNumbers) {
    return hasElement(JsonPointer.from("/callNumber/callNumber"), callNumbers);
  }

  /**
   * Verify the size of items. All that belong to one holding and one instance are grouped together.
   *
   * @param size - size of expected aggregated items
   */
  public static Matcher<JsonObject> hasAggregatedNumberOfItems(int size) {
    return hasItemsCount(size);
  }

  public static Matcher<JsonObject> hasEffectiveLocationInstitutionName(String... institutionNames) {
    return hasElement(JsonPointer.from("/location/location/institutionName"), institutionNames);
  }

  private static Matcher<JsonObject> hasElement(JsonPointer jsonPointer, String[] expectedValue) {

    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Expected: ")
          .appendValue(expectedValue);
      }

      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        final JsonArray items = (JsonArray) itemFieldsPointer.queryJson(jsonObject);
        if (items == null) {
          return false;
        }
        final List<String> actualValues = items.stream()
          .map(jsonPointer::queryJson)
          .map(String.class::cast)
          .toList();

        return Arrays.asList(expectedValue)
          .containsAll(actualValues);
      }
    };
  }

  private static Matcher<JsonObject> hasItemsCount(int expectedValue) {

    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Has number of items ")
          .appendValue(expectedValue);
      }

      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        final JsonArray items = (JsonArray) itemFieldsPointer.queryJson(jsonObject);
        return jsonObject.isEmpty() || items == null || items.size() == expectedValue;
      }
    };
  }
}
