package org.folio.rest.support.matchers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
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

  private static <T> Matcher<JsonObject> hasRootElement(JsonPointer jsonPointer, String[] expectedValue) {

    return new TypeSafeMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        final Object actualValue = jsonPointer.queryJson(jsonObject);

        return Arrays.asList(expectedValue)
          .containsAll(Collections.singleton(actualValue));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Expected: ")
          .appendValue(expectedValue);
      }
    };
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

  private static <T> Matcher<JsonObject> hasElementCount(JsonPointer jsonPointer, int expectedValue) {

    return new TypeSafeMatcher<JsonObject>() {
      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        final JsonArray items = (JsonArray) jsonPointer.queryJson(jsonObject);
        return jsonObject.isEmpty() || items == null || items.size() == expectedValue;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(String.format("Has number of '%s' elements ", jsonPointer.toString()))
          .appendValue(expectedValue);
      }
    };
  }

  private static <T> Matcher<JsonObject> hasInstanceElement(JsonPointer jsonPointer, String[] expectedValue) {
    return hasRootElement(jsonPointer, expectedValue);
  }

    private static <T> Matcher<JsonObject> hasHoldingsElement(JsonPointer jsonPointer, String[] expectedValue) {
    return hasElement(holdingsFieldsPointer, jsonPointer, expectedValue);
  }

  private static <T> Matcher<JsonObject> hasItemsElement(JsonPointer jsonPointer, String[] expectedValue) {
    return hasElement(itemsFieldsPointer, jsonPointer, expectedValue);
  }

  private static <T> Matcher<JsonObject> hasItemsCount(int expectedValue) {
    return hasElementCount(itemsFieldsPointer, expectedValue);
  }

  private static <T> Matcher<JsonObject> hasHoldingsCount(int expectedValue) {
    return hasElementCount(holdingsFieldsPointer, expectedValue);
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
   * Verify instance presence
   */
  public static Matcher<JsonObject> hasIdForInstance(String instanceId) {
    return hasInstanceElement(JsonPointer.from("/instanceId"), ArrayUtils.toArray(instanceId));
  }

  public static Matcher<JsonObject> hasSourceForInstance(String source) {
    return hasInstanceElement(JsonPointer.from("/source"), ArrayUtils.toArray(source));
  }

  /**
   * Verify holdings structure
   */
  public static Matcher<JsonObject> hasIdForHoldings(String holdingId) {
    return hasHoldingsElement(JsonPointer.from("/id"), ArrayUtils.toArray(holdingId));
  }

  public static Matcher<JsonObject> hasPermanentLocationForHoldings(String permanentLocation) {
    return hasHoldingsElement(JsonPointer.from("/location/permanentLocation/name"), ArrayUtils.toArray(permanentLocation));
  }

  public static Matcher<JsonObject> hasAggregatedNumberOfHoldings(int size) {
    return hasHoldingsCount(size);
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

  public static Matcher<JsonObject> hasIdForItems(String... callNumbers) {
    return hasItemsElement(JsonPointer.from("/id"), callNumbers);
  }


}
