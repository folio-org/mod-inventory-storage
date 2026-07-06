package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.AwaitConfiguration.awaitDuring;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.services.domainevent.SettingEvent;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

/**
 * Utility class for checking setting event messages published to Kafka.
 */
public class SettingEventMessageChecks {

  private final FakeKafkaConsumer kafkaConsumer;

  public SettingEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
  }

  /**
   * Verifies that a setting event message was published with all matching fields.
   *
   * @param settingEvent the expected setting event
   */
  public void settingEventPublished(SettingEvent settingEvent) {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForSetting(settingEvent.id()),
      hasItem(hasAllSettingEventFields(settingEvent.id(), settingEvent.key(), settingEvent.value().toString(),
        settingEvent.tenantId())));
  }

  /**
   * Verifies that a setting event message was published for the given setting ID
   * and validates all fields in the event body (id, key, value, tenantId).
   *
   * @param settingId the ID of the setting
   */
  public void settingEventPublished(String settingId) {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForSetting(settingId),
      hasItem(hasAllSettingEventFieldsPresent(settingId)));
  }

  public void settingEventNotPublished(String settingId) {
    awaitDuring(3, SECONDS).until(() -> kafkaConsumer.getMessagesForSetting(settingId), empty());
  }

  /**
   * Verifies that a setting event message was published for the given setting ID and tenant.
   *
   * @param settingId the ID of the setting
   * @param tenantId  the expected tenant ID
   */
  public void settingEventPublishedForTenant(String settingId, String tenantId) {
    awaitAtMost().until(() -> kafkaConsumer.getMessagesForSetting(settingId),
      hasItem(hasProperty("body", hasJsonProperty("tenantId", tenantId))));
  }

  /**
   * Creates a matcher for checking all fields in the setting event.
   * SettingEvent has: id, key, value, tenantId
   */
  private Matcher<EventMessage> hasAllSettingEventFields(String id, String key, String value, String tenantId) {
    return Matchers.allOf(
      hasProperty("body", hasJsonProperty("id", id)),
      hasProperty("body", hasJsonProperty("key", key)),
      hasProperty("body", hasJsonProperty("value", value)),
      hasProperty("body", hasJsonProperty("tenantId", tenantId))
    );
  }

  /**
   * Creates a matcher for checking all fields are present in the setting event,
   * with id matching the expected value and other fields being non-null.
   * SettingEvent has: id, key, value, tenantId
   */
  private Matcher<EventMessage> hasAllSettingEventFieldsPresent(String expectedId) {
    return Matchers.allOf(
      hasProperty("body", hasJsonProperty("id", expectedId)),
      hasProperty("body", hasJsonPropertyNotNull("key")),
      hasProperty("body", hasJsonPropertyNotNull("value")),
      hasProperty("body", hasJsonPropertyNotNull("tenantId"))
    );
  }

  /**
   * Creates a matcher for checking a JSON property value in a JsonObject.
   */
  @SuppressWarnings("checkstyle:MethodLength")
  private Matcher<JsonObject> hasJsonProperty(String propertyName, String expectedValue) {
    return new org.hamcrest.TypeSafeMatcher<>() {
      @Override
      protected boolean matchesSafely(JsonObject json) {
        if (json == null) {
          return false;
        }
        Object actualValue = json.getValue(propertyName);
        if (actualValue == null) {
          return expectedValue == null;
        }
        return actualValue.toString().equals(expectedValue);
      }

      @Override
      public void describeTo(org.hamcrest.Description description) {
        description.appendText("JSON with property '")
          .appendText(propertyName).appendText("' = '").appendText(expectedValue).appendText("'");
      }

      @Override
      protected void describeMismatchSafely(JsonObject json, org.hamcrest.Description mismatchDescription) {
        if (json == null) {
          mismatchDescription.appendText("was null");
        } else {
          mismatchDescription.appendText("was '").appendText(String.valueOf(json.getValue(propertyName)))
            .appendText("'");
        }
      }
    };
  }

  /**
   * Creates a matcher for checking a JSON property exists and is not null in a JsonObject.
   */
  private Matcher<JsonObject> hasJsonPropertyNotNull(String propertyName) {
    return new org.hamcrest.TypeSafeMatcher<>() {
      @Override
      protected boolean matchesSafely(JsonObject json) {
        if (json == null) {
          return false;
        }
        return json.getValue(propertyName) != null;
      }

      @Override
      public void describeTo(org.hamcrest.Description description) {
        description.appendText("JSON with non-null property '").appendText(propertyName).appendText("'");
      }

      @Override
      protected void describeMismatchSafely(JsonObject json, org.hamcrest.Description mismatchDescription) {
        if (json == null) {
          mismatchDescription.appendText("JSON was null");
        } else {
          mismatchDescription.appendText("property '").appendText(propertyName).appendText("' was null");
        }
      }
    };
  }
}

