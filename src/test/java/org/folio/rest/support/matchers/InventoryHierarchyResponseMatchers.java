package org.folio.rest.support.matchers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;

public final class InventoryHierarchyResponseMatchers {

  static JsonPointer holdingsFieldsPointer = JsonPointer.from("/holdings");
  static JsonPointer itemsFieldsPointer = JsonPointer.from("/items");

  private InventoryHierarchyResponseMatchers() {
  }

  private static <T> Matcher<JsonObject> hasItemsElement(JsonPointer jsonPointer, String[] expectedValue) {
    return hasElement(itemsFieldsPointer, jsonPointer, expectedValue);
  }

  private static <T> Matcher<JsonObject> hasHoldingsElement(JsonPointer rootJsonPointer, JsonPointer jsonPointer, String[] expectedValue) {
    return hasElement(holdingsFieldsPointer, jsonPointer, expectedValue);
  }

  private static <T> Matcher<JsonObject> hasElement(JsonPointer rootJsonPointer, JsonPointer jsonPointer, String[] expectedValue) {

    return new TypeSafeMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        // Items matching
        final JsonArray items = (JsonArray) rootJsonPointer.queryJson(jsonObject);
        if (items == null) {
          return false;
        }
        final List<Object> actualValues = items.stream()
          .map(jsonPointer::queryJson)
          .collect(Collectors.toList());

        return Arrays.asList(expectedValue)
          .containsAll(actualValues);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Expected: ")
          .appendValue(expectedValue);
      }
    };
  }

  private static <T> Matcher<JsonObject> hasItemsCount(int expectedValue) {

    return new TypeSafeMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        final JsonArray items = (JsonArray) itemsFieldsPointer.queryJson(jsonObject);
        return jsonObject.isEmpty() || items == null || items.size() == expectedValue;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Has number of items ")
          .appendValue(expectedValue);
      }
    };
  }

  public static <T> Matcher<JsonObject> isDeleted() {

    return new TypeSafeMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        final Object deleted = JsonPointer.from("/deleted")
          .queryJson(jsonObject);
        return Boolean.parseBoolean(deleted.toString());
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Is deleted ")
          .appendValue(true);
      }
    };
  }

  /**
   * Verify the size of items. All that belong to one holding and one instance are grouped together.
   * @param size - size of expected aggregated items
   *
   */
  public static Matcher<JsonObject> hasAggregatedNumberOfItems(int size) {
    return hasItemsCount(size);
  }

  public static Matcher<JsonObject> hasEffectiveLocationInstitutionNameForItems(String... institutionNames) {
    return hasItemsElement(JsonPointer.from("/location/location/institutionName"), institutionNames);
  }

  public static Matcher<JsonObject> hasCallNumberForItems(String... callNumbers) {
    return hasItemsElement(JsonPointer.from("/callNumber/callNumber"), callNumbers);
  }



}
