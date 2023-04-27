package org.folio.rest.support.matchers;

import io.vertx.core.json.JsonObject;
import java.util.Objects;
import java.util.function.Function;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public final class ItemMatchers {
  private static final String EFFECTIVE_CALL_NUMBER_COMPONENTS = "effectiveCallNumberComponents";

  private ItemMatchers() {
  }

  public static Matcher<JsonObject> effectiveCallNumberComponents(
    Matcher<EffectiveCallNumberComponents> matcher) {

    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Has effective call number components matches ")
          .appendDescriptionOf(matcher);
      }

      @Override
      protected boolean matchesSafely(JsonObject jsonObject) {
        if (!jsonObject.containsKey(EFFECTIVE_CALL_NUMBER_COMPONENTS)) {
          return false;
        }

        final EffectiveCallNumberComponents components = jsonObject
          .getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS)
          .mapTo(EffectiveCallNumberComponents.class);

        return matcher.matches(components);
      }

      @Override
      protected void describeMismatchSafely(JsonObject item, Description mismatchDescription) {
        super.describeMismatchSafely(item.getJsonObject(EFFECTIVE_CALL_NUMBER_COMPONENTS),
          mismatchDescription);
      }
    };
  }

  private static <T> Matcher<EffectiveCallNumberComponents> hasEffectiveCallNumberElement(
    Function<EffectiveCallNumberComponents, T> property, T expectedValue) {

    return new TypeSafeMatcher<EffectiveCallNumberComponents>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Has effective call number component value ")
          .appendValue(expectedValue);
      }

      @Override
      protected boolean matchesSafely(EffectiveCallNumberComponents components) {
        final T actualValue = property.apply(components);
        return Objects.equals(actualValue, expectedValue);
      }
    };
  }

  public static Matcher<EffectiveCallNumberComponents> hasCallNumber(String callNumber) {
    return hasEffectiveCallNumberElement(EffectiveCallNumberComponents::getCallNumber,
      callNumber);
  }

  public static Matcher<EffectiveCallNumberComponents> hasSuffix(String suffix) {
    return hasEffectiveCallNumberElement(EffectiveCallNumberComponents::getSuffix,
      suffix);
  }

  public static Matcher<EffectiveCallNumberComponents> hasPrefix(String prefix) {
    return hasEffectiveCallNumberElement(EffectiveCallNumberComponents::getPrefix,
      prefix);
  }

  public static Matcher<EffectiveCallNumberComponents> hasTypeId(String typeId) {
    return hasEffectiveCallNumberElement(EffectiveCallNumberComponents::getTypeId,
      typeId);
  }
}

