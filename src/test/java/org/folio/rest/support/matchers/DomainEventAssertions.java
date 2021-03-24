package org.folio.rest.support.matchers;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static java.util.UUID.fromString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
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
import static org.junit.Assert.assertEquals;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.folio.services.kafka.KafkaMessage;

public final class DomainEventAssertions {
  private DomainEventAssertions() {}

  private static void assertCreateEvent(KafkaMessage<JsonObject> createEvent, JsonObject newRecord) {
    assertThat(createEvent.getPayload().getString("type"), is("CREATE"));
    assertThat(createEvent.getPayload().getString("tenant"), is(TENANT_ID));
    assertThat(createEvent.getPayload().getJsonObject("old"), nullValue());
    assertThat(createEvent.getPayload().getJsonObject("new"), is(newRecord));

    assertHeaders(createEvent.getHeaders());
  }

  private static void assertRemoveEvent(KafkaMessage<JsonObject> deleteEvent, JsonObject record) {
    assertThat(deleteEvent.getPayload().getString("type"), is("DELETE"));
    assertThat(deleteEvent.getPayload().getString("tenant"), is(TENANT_ID));
    assertThat(deleteEvent.getPayload().getJsonObject("new"), nullValue());
    assertThat(deleteEvent.getPayload().getJsonObject("old"), is(record));

    assertHeaders(deleteEvent.getHeaders());
  }

  private static void assertRemoveAllEvent(KafkaMessage<JsonObject> deleteEvent) {
    assertThat(deleteEvent.getPayload().getString("type"), is("DELETE_ALL"));
    assertThat(deleteEvent.getPayload().getString("tenant"), is(TENANT_ID));
    assertThat(deleteEvent.getPayload().getJsonObject("new"), nullValue());
    assertThat(deleteEvent.getPayload().getJsonObject("old"), nullValue());

    assertHeaders(deleteEvent.getHeaders());
  }

  public static void assertUpdateEvent(
    KafkaMessage<JsonObject> updateEvent, JsonObject oldRecord, JsonObject newRecord) {

    assertThat(updateEvent.getPayload().getString("type"), is("UPDATE"));
    assertThat(updateEvent.getPayload().getString("tenant"), is(TENANT_ID));
    assertThat(updateEvent.getPayload().getJsonObject("old"), is(oldRecord));
    assertThat(updateEvent.getPayload().getJsonObject("new"), is(newRecord));

    assertHeaders(updateEvent.getHeaders());
  }

  private static void assertHeaders(Map<String, String> headers) {
    final MultiMap caseInsensitiveMap = caseInsensitiveMultiMap().addAll(headers);

    assertEquals(caseInsensitiveMap.size(), 2);
    assertEquals(caseInsensitiveMap.get(TENANT), TENANT_ID);
    assertEquals(caseInsensitiveMap.get(URL), storageUrl("").toString());
  }

  public static void assertNoUpdateEvent(String instanceId) {
    await().atLeast(1, SECONDS);
    await().until(() -> getInstanceEvents(instanceId).size() > 0);

    final JsonObject updateMessage  = getLastInstanceEvent(instanceId).getPayload();
    assertThat(updateMessage.getString("type"), not(is("UPDATE")));
  }

  public static void assertNoUpdateEventForHolding(String instanceId, String hrId) {
    await().atLeast(1, SECONDS);
    await().until(() -> getHoldingsEvents(instanceId, hrId).size() > 0);

    final JsonObject updateMessage  = getLastHoldingEvent(instanceId, hrId).getPayload();
    assertThat(updateMessage.getString("type"), not(is("UPDATE")));
  }

  public static void assertNoRemoveEvent(String instanceId) {
    await().atLeast(1, SECONDS);
    await().until(() -> getInstanceEvents(instanceId).size() > 0);

    final JsonObject updateMessage  = getLastInstanceEvent(instanceId).getPayload();
    assertThat(updateMessage.getString("type"), not(is("DELETE")));
  }

  public static void assertNoCreateEvent(String instanceId) {
    await().atLeast(1, SECONDS);

    assertThat(getInstanceEvents(instanceId).size(), is(0));
  }

  public static void assertCreateEventForInstance(JsonObject instance) {
    final String instanceId = instance.getString("id");

    await().until(() -> getInstanceEvents(instanceId).size() > 0);

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

    await().until(() -> getInstanceEvents(instanceId).size() > 1);

    assertRemoveEvent(getLastInstanceEvent(instanceId), instance);
  }

  public static void assertRemoveAllEventForInstance() {
    await().until(() -> getInstanceEvents(NULL_INSTANCE_ID).size() > 0);

    assertRemoveAllEvent(getLastInstanceEvent(NULL_INSTANCE_ID));
  }

  public static void assertUpdateEventForInstance(JsonObject oldInstance, JsonObject newInstance) {
    final String instanceId = oldInstance.getString("id");

    await().until(() -> getInstanceEvents(instanceId).size() > 1);

    assertUpdateEvent(getLastInstanceEvent(instanceId), oldInstance, newInstance);
  }

  public static void assertCreateEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);

    await().until(() -> getItemEvents(instanceIdForItem, itemId).size() > 0);

    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertCreateEvent(getFirstItemEvent(instanceIdForItem, itemId),
      addInstanceIdForItem(item, instanceIdForItem));
  }

  public static void assertRemoveEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);

    await().until(() -> getItemEvents(instanceIdForItem, itemId).size() > 1);

    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    assertRemoveEvent(getLastItemEvent(instanceIdForItem, itemId),
      addInstanceIdForItem(item, instanceIdForItem));
  }

  public static void assertRemoveAllEventForItem() {
    await().until(() -> getItemEvents(NULL_INSTANCE_ID, null).size() > 0);

    assertRemoveAllEvent(getLastItemEvent(NULL_INSTANCE_ID, null));
  }

  public static void assertUpdateEventForItem(JsonObject oldItem, JsonObject newItem) {
    final String itemId = newItem.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(newItem);

    await().until(() -> getItemEvents(instanceIdForItem, itemId).size() > 1);

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

    await().until(() -> getHoldingsEvents(instanceId, id).size() > 0);

    assertCreateEvent(getFirstHoldingEvent(instanceId, id), hr);
  }

  public static void assertRemoveEventForHolding(JsonObject hr) {
    final String id = hr.getString("id");
    final String instanceId = hr.getString("instanceId");

    await().until(() -> getHoldingsEvents(instanceId, id).size() > 1);

    assertRemoveEvent(getLastHoldingEvent(instanceId, id), hr);
  }

  public static void assertRemoveAllEventForHolding() {
    await().until(() -> getHoldingsEvents(NULL_INSTANCE_ID, null).size() > 0);

    assertRemoveAllEvent(getLastHoldingEvent(NULL_INSTANCE_ID, null));
  }

  public static void assertUpdateEventForHolding(JsonObject oldHr, JsonObject newHr) {
    final String id = newHr.getString("id");
    final String instanceId = newHr.getString("instanceId");

    await().until(() -> getHoldingsEvents(instanceId, id).size() > 1);

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
