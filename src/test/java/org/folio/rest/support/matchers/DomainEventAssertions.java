package org.folio.rest.support.matchers;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static java.util.UUID.fromString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.folio.rest.api.TestBase.holdingsClient;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstHoldingEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstInstanceEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstItemEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getHoldingsEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getInstanceEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getItemEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastHoldingEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastInstanceEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastItemEvent;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_INSTANCE_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

public final class DomainEventAssertions {
  private DomainEventAssertions() { }

  private static void assertCreateEvent(KafkaConsumerRecord<String, JsonObject> createEvent, JsonObject newRecord) {
    assertThat("Create event should be present", createEvent.value(), is(notNullValue()));
    assertThat(createEvent.value().getString("type"), is("CREATE"));
    assertThat(createEvent.value().getString("tenant"), is(TENANT_ID));
    assertThat(createEvent.value().getJsonObject("old"), nullValue());
    assertThat(createEvent.value().getJsonObject("new"), is(newRecord));

    assertHeaders(createEvent.headers());
  }

  private static void assertRemoveEvent(KafkaConsumerRecord<String, JsonObject> deleteEvent, JsonObject record) {
    assertThat(deleteEvent.value().getString("type"), is("DELETE"));
    assertThat(deleteEvent.value().getString("tenant"), is(TENANT_ID));
    assertThat(deleteEvent.value().getJsonObject("new"), nullValue());
    assertThat(deleteEvent.value().getJsonObject("old"), is(record));

    assertHeaders(deleteEvent.headers());
  }

  private static void assertRemoveAllEvent(KafkaConsumerRecord<String, JsonObject> deleteEvent) {
    assertThat(deleteEvent.value().getString("type"), is("DELETE_ALL"));
    assertThat(deleteEvent.value().getString("tenant"), is(TENANT_ID));
    assertThat(deleteEvent.value().getJsonObject("new"), nullValue());
    assertThat(deleteEvent.value().getJsonObject("old"), nullValue());

    assertHeaders(deleteEvent.headers());
  }

  public static void assertUpdateEvent(
          KafkaConsumerRecord<String, JsonObject> updateEvent, JsonObject oldRecord, JsonObject newRecord) {

    assertThat(updateEvent.value().getString("type"), is("UPDATE"));
    assertThat(updateEvent.value().getString("tenant"), is(TENANT_ID));
    assertThat(updateEvent.value().getJsonObject("old"), is(oldRecord));
    assertThat(updateEvent.value().getJsonObject("new"), is(newRecord));

    assertHeaders(updateEvent.headers());
  }

  private static void assertHeaders(List<KafkaHeader> headers) {
    final MultiMap caseInsensitiveMap = caseInsensitiveMultiMap()
      .addAll(kafkaHeadersToMap(headers));

    assertEquals(caseInsensitiveMap.size(), 2);
    assertEquals(caseInsensitiveMap.get(TENANT), TENANT_ID);
    assertEquals(caseInsensitiveMap.get(URL), storageUrl("").toString());
  }

  public static void assertNoUpdateEvent(String instanceId) {
    await().atLeast(1, SECONDS);
    await().atMost(1, SECONDS)
      .until(() -> getInstanceEvents(instanceId).size(), greaterThan(0));

    final JsonObject updateMessage  = getLastInstanceEvent(instanceId).value();
    assertThat(updateMessage.getString("type"), not(is("UPDATE")));
  }

  public static void assertNoUpdateEventForHolding(String instanceId, String hrId) {
    await().atLeast(1, SECONDS);
    await().atMost(1, SECONDS)
      .until(() -> getHoldingsEvents(instanceId, hrId).size(), greaterThan(0));

    final JsonObject updateMessage  = getLastHoldingEvent(instanceId, hrId).value();
    assertThat(updateMessage.getString("type"), not(is("UPDATE")));
  }

  public static void assertNoRemoveEvent(String instanceId) {
    await().atLeast(1, SECONDS);
    await().atMost(1, SECONDS)
      .until(() -> getInstanceEvents(instanceId).size(), greaterThan(0));

    final JsonObject updateMessage  = getLastInstanceEvent(instanceId).value();
    assertThat(updateMessage.getString("type"), not(is("DELETE")));
  }

  public static void assertNoCreateEvent(String instanceId) {
    await().atLeast(1, SECONDS);

    assertThat(getInstanceEvents(instanceId).size(), is(0));
  }

  public static void assertCreateEventForInstance(JsonObject instance) {
    final String instanceId = instance.getString("id");

    await().atMost(1, SECONDS)
      .until(() -> getInstanceEvents(instanceId).size(), greaterThan(0));

    assertCreateEvent(getFirstInstanceEvent(instanceId), instance);
  }

  public static void assertCreateEventForInstances(JsonArray instances) {
    assertCreateEventForInstances(instances.stream()
      .map(obj -> (JsonObject) obj)
      .collect(Collectors.toList()));
  }

  public static void assertCreateEventForInstances(List<JsonObject> instances) {
    instances.forEach(instance -> {
      final String instanceId = instance.getString("id");
      assertCreateEvent(getFirstInstanceEvent(instanceId), instance);
    });
  }

  public static void assertRemoveEventForInstance(JsonObject instance) {
    final String instanceId = instance.getString("id");

    await().atMost(1, SECONDS)
      .until(() -> getInstanceEvents(instanceId).size(), greaterThan(1));

    assertRemoveEvent(getLastInstanceEvent(instanceId), instance);
  }

  public static void assertRemoveAllEventForInstance() {
    await().atMost(1, SECONDS)
      .until(() -> getInstanceEvents(NULL_INSTANCE_ID).size(), greaterThan(0));

    assertRemoveAllEvent(getLastInstanceEvent(NULL_INSTANCE_ID));
  }

  public static void assertUpdateEventForInstance(JsonObject oldInstance, JsonObject newInstance) {
    final String instanceId = oldInstance.getString("id");

    await().atMost(1, SECONDS)
      .until(() -> getInstanceEvents(instanceId).size(), greaterThan(1));

    assertUpdateEvent(getLastInstanceEvent(instanceId), oldInstance, newInstance);
  }

  public static void assertCreateEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);

    await().atMost(1, SECONDS)
      .until(() -> getItemEvents(instanceIdForItem, itemId).size(), greaterThan(0));

    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertCreateEvent(getFirstItemEvent(instanceIdForItem, itemId),
      addInstanceIdForItem(item, instanceIdForItem));
  }

  public static void assertRemoveEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);

    await().atMost(1, SECONDS)
      .until(() -> getItemEvents(instanceIdForItem, itemId).size(), greaterThan(1));

    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertRemoveEvent(getLastItemEvent(instanceIdForItem, itemId),
      addInstanceIdForItem(item, instanceIdForItem));
  }

  public static void assertRemoveAllEventForItem() {
    await().atMost(1, SECONDS)
      .until(() -> getItemEvents(NULL_INSTANCE_ID, null).size(), greaterThan(0));

    assertRemoveAllEvent(getLastItemEvent(NULL_INSTANCE_ID, null));
  }

  public static void assertUpdateEventForItem(JsonObject oldItem, JsonObject newItem) {
    final String itemId = newItem.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(newItem);

    await().atMost(1, SECONDS)
      .until(() -> getItemEvents(instanceIdForItem, itemId).size(), greaterThan(1));

    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertUpdateEvent(getLastItemEvent(instanceIdForItem, itemId),
      addInstanceIdForItem(oldItem, getInstanceIdForItem(oldItem)),
      addInstanceIdForItem(newItem, instanceIdForItem));
  }

  public static void assertCreateEventForHolding(JsonObject hr) {
    final String id = hr.getString("id");
    final String instanceId = hr.getString("instanceId");

    await().atMost(1, SECONDS)
      .until(() -> getHoldingsEvents(instanceId, id).size(), greaterThan(0));

    assertCreateEvent(getFirstHoldingEvent(instanceId, id), hr);
  }

  public static void assertRemoveEventForHolding(JsonObject hr) {
    final String id = hr.getString("id");
    final String instanceId = hr.getString("instanceId");

    await().atMost(1, SECONDS)
      .until(() -> getHoldingsEvents(instanceId, id).size(), greaterThan(1));

    assertRemoveEvent(getLastHoldingEvent(instanceId, id), hr);
  }

  public static void assertRemoveAllEventForHolding() {
    await().atMost(1, SECONDS)
      .until(() -> getHoldingsEvents(NULL_INSTANCE_ID, null).size(), greaterThan(0));

    assertRemoveAllEvent(getLastHoldingEvent(NULL_INSTANCE_ID, null));
  }

  public static void assertUpdateEventForHolding(JsonObject oldHr, JsonObject newHr) {
    final String id = newHr.getString("id");
    final String instanceId = newHr.getString("instanceId");

    await().atMost(1, SECONDS)
      .until(() -> getHoldingsEvents(instanceId, id).size(), greaterThan(1));

    assertUpdateEvent(getLastHoldingEvent(instanceId, id), oldHr, newHr);
  }

  private static String getInstanceIdForItem(JsonObject newItem) {
    final UUID holdingsRecordId = fromString(newItem.getString("holdingsRecordId"));

    return holdingsClient.getById(holdingsRecordId).getJson().getString("instanceId");
  }

  private static JsonObject addInstanceIdForItem(JsonObject item, String instanceId) {
    return item.copy().put("instanceId", instanceId);
  }
}
