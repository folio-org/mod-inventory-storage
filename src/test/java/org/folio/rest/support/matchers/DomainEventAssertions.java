package org.folio.rest.support.matchers;

import static java.util.UUID.fromString;
import static org.awaitility.Awaitility.await;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.TestBase.holdingsClient;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstInstanceEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstItemEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getInstanceEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getItemEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastInstanceEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastItemEvent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.JsonObject;

public final class DomainEventAssertions {
  private DomainEventAssertions() {}

  private static void assertCreateEvent(JsonObject createEvent, JsonObject newRecord) {
    assertThat(createEvent.getString("type"), is("CREATE"));
    assertThat(createEvent.getString("tenant"), is(TENANT_ID));
    assertThat(createEvent.getJsonObject("old"), nullValue());
    assertThat(createEvent.getJsonObject("new"), is(newRecord));
  }

  private static void assertRemoveEvent(JsonObject deleteEvent, JsonObject record) {
    assertThat(deleteEvent.getString("type"), is("DELETE"));
    assertThat(deleteEvent.getString("tenant"), is(TENANT_ID));
    assertThat(deleteEvent.getJsonObject("new"), nullValue());
    assertThat(deleteEvent.getJsonObject("old"), is(record));
  }

  private static void assertUpdateEvent(
    JsonObject updateEvent, JsonObject oldRecord, JsonObject newRecord) {

    assertThat(updateEvent.getString("type"), is("UPDATE"));
    assertThat(updateEvent.getString("tenant"), is(TENANT_ID));
    assertThat(updateEvent.getJsonObject("old"), is(oldRecord));
    assertThat(updateEvent.getJsonObject("new"), is(newRecord));
  }

  public static void assertNoUpdateEvent(String instanceId) {
    await().atLeast(1, TimeUnit.SECONDS);

    final JsonObject updateMessage  = getLastInstanceEvent(instanceId);
    assertThat(updateMessage.getString("type"), not(is("UPDATE")));
  }

  public static void assertNoRemoveEvent(String instanceId) {
    await().atLeast(1, TimeUnit.SECONDS);

    final JsonObject updateMessage  = getLastInstanceEvent(instanceId);
    assertThat(updateMessage.getString("type"), not(is("DELETE")));
  }

  public static void assertNoCreateEvent(String instanceId) {
    await().atLeast(1, TimeUnit.SECONDS);

    assertThat(getInstanceEvents(instanceId).size(), is(0));
  }

  public static void assertCreateEventForInstance(JsonObject instance) {
    final String instanceId = instance.getString("id");

    await().until(() -> getInstanceEvents(instanceId).size() > 0);

    assertCreateEvent(getFirstInstanceEvent(instanceId), instance);
  }

  public static void assertRemoveEventForInstance(JsonObject instance) {
    final String instanceId = instance.getString("id");

    await().until(() -> getInstanceEvents(instanceId).size() > 0);

    assertRemoveEvent(getLastInstanceEvent(instanceId), instance);
  }

  public static void assertUpdateEventForInstance(JsonObject oldInstance, JsonObject newInstance) {
    final String instanceId = oldInstance.getString("id");

    await().until(() -> getInstanceEvents(instanceId).size() > 0);

    assertUpdateEvent(getLastInstanceEvent(instanceId), oldInstance, newInstance);
  }

  public static void assertCreateEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);

    await().until(() -> getItemEvents(instanceIdForItem, itemId).size() > 0);

    final JsonObject message = getFirstItemEvent(instanceIdForItem, itemId);
    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertCreateEvent(message, addInstanceIdForItem(item, instanceIdForItem));
  }

  public static void assertRemoveEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);

    await().until(() -> getItemEvents(instanceIdForItem, itemId).size() > 1);

    final JsonObject message = getLastItemEvent(instanceIdForItem, itemId);
    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertRemoveEvent(message, addInstanceIdForItem(item, instanceIdForItem));
  }

  public static void assertUpdateEventForItem(JsonObject oldItem, JsonObject newItem) {
    final String itemId = newItem.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(newItem);

    await().until(() -> getItemEvents(instanceIdForItem, itemId).size() > 0);

    final JsonObject message = getLastItemEvent(instanceIdForItem, itemId);
    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertUpdateEvent(message,
      addInstanceIdForItem(oldItem, getInstanceIdForItem(oldItem)),
      addInstanceIdForItem(newItem, instanceIdForItem));
  }

  private static String getInstanceIdForItem(JsonObject newItem) {
    final UUID holdingsRecordId = fromString(newItem.getString("holdingsRecordId"));

    return holdingsClient.getById(holdingsRecordId).getJson().getString("instanceId");
  }

  private static JsonObject addInstanceIdForItem(JsonObject item, String instanceId) {
    return item.copy().put("instanceId", instanceId);
  }
}
