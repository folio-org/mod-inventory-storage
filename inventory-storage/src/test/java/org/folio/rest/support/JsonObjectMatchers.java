package org.folio.rest.support;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class JsonObjectMatchers {

  public static Matcher identifierMatches(String namespace, String value) {

    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "an identifier with namespace: %s and value: %s", namespace, value));
      }

      @Override
      protected boolean matchesSafely(JsonObject entries) {
        return entries.getString("namespace").equals(namespace)
          && entries.getString("value").equals(value);
      }
    };
  }
}
