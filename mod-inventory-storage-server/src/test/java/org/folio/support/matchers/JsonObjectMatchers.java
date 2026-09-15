package org.folio.support.matchers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Objects;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class JsonObjectMatchers {

  /**
   * Ignores change metadata because created and updated date might be represented
   * with either +00:00 or Z due to differences in serialization / deserialization.
   *
   * @param expectedRepresentation expected representation of the record
   * @return a Hamcrest matcher
   */
  public static Matcher<JsonObject> equalsIgnoringMetadata(JsonObject expectedRepresentation) {
    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(
          "a JsonObject being equal when ignoring metadata property: " + expectedRepresentation);
      }

      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        var finalJsonObject = jsonObject.copy();
        var finalExpected = expectedRepresentation.copy();
        finalJsonObject.remove("metadata");
        finalExpected.remove("metadata");
        return finalJsonObject.equals(finalExpected);
      }
    };
  }

  public static List<JsonObject> toList(JsonArray array) {
    return array
      .stream()
      .map(item -> {
        if (item instanceof JsonObject) {
          return (JsonObject) item;
        } else {
          return null;
        }
      })
      .filter(Objects::nonNull)
      .toList();
  }
}
