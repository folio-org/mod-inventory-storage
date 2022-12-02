package org.folio.rest.support.matchers;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static java.util.UUID.fromString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.api.TestBase.holdingsClient;
import static org.folio.rest.support.JsonObjectMatchers.equalsIgnoringMetadata;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getAuthorityEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstAuthorityEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstHoldingEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstInstanceEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getFirstItemEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getHoldingsEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getInstanceEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getItemEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastAuthorityEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastHoldingEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastInstanceEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastItemEvent;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getMessagesForInstance;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_INSTANCE_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

public final class DomainEventAssertions {
  private DomainEventAssertions() { }

  /**
   * Awaitility.await() with a default timeout of 2 seconds.
   *
   * <p>1 second is too short:
   * <a href="https://issues.folio.org/browse/MODINVSTOR-754">MODINVSTOR-754</a>
   */
  private static ConditionFactory await() {
    return Awaitility.await().atMost(10, SECONDS);
  }

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

    // ignore metadata because +00:00 ends as Z after createdDate and updatedDate have been
    // deserialized from JSON to POJO resulting in a Date and serialized from POJO to JSON
    assertThat(deleteEvent.value().getJsonObject("old"), equalsIgnoringMetadata(record));

    assertHeaders(deleteEvent.headers());
  }

  private static boolean hasRemoveEvent(Collection<KafkaConsumerRecord<String, JsonObject>> events, JsonObject record) {
    if (events == null) {
      return false;
    }
    for (var event : events) {
      try {
        assertRemoveEvent(event, record);
        return true;
      } catch (AssertionError e) {
        // ignore
      }
    }
    return false;
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

  public static boolean hasUpdateEvent(Collection<KafkaConsumerRecord<String, JsonObject>> events,
      JsonObject oldRecord, JsonObject newRecord) {

    if (events == null) {
      return false;
    }
    for (var event : events) {
      try {
        assertUpdateEvent(event, oldRecord, newRecord);
        return true;
      } catch (AssertionError e) {
        // ignore
      }
    }
    return false;
  }

  private static void assertHeaders(List<KafkaHeader> headers) {
    final MultiMap caseInsensitiveMap = caseInsensitiveMultiMap()
      .addAll(kafkaHeadersToMap(headers));

    assertEquals(2, caseInsensitiveMap.size());
    assertEquals(TENANT_ID, caseInsensitiveMap.get(TENANT));
    assertEquals(vertxUrl("").toString(), caseInsensitiveMap.get(URL));
  }

  public static void assertNoUpdateEvent(String instanceId) {
    await()
      .until(() -> getInstanceEvents(instanceId), is(not(empty())));

    final JsonObject updateMessage  = getLastInstanceEvent(instanceId).value();
    assertThat(updateMessage.getString("type"), not(is("UPDATE")));
  }

  public static void assertNoUpdateEventForHolding(String instanceId, String hrId) {
    await()
      .until(() -> getHoldingsEvents(instanceId, hrId), is(not(empty())));

    final JsonObject updateMessage  = getLastHoldingEvent(instanceId, hrId).value();
    assertThat(updateMessage.getString("type"), not(is("UPDATE")));
  }

  public static void assertCreateEventForAuthority(JsonObject authority) {
    final String id = authority.getString("id");

    await()
      .until(() -> getAuthorityEvents(id).size(), greaterThan(0));

    assertCreateEvent(getFirstAuthorityEvent(id), authority);
  }

  public static void noInstanceMessagesPublished(String instanceId) {
    await().during(1, SECONDS)
      .until(() -> getMessagesForInstance(instanceId), is(empty()));
  }

  public static void instanceCreatedMessagePublished(JsonObject instance) {
    final String instanceId = instance.getString("id");

    final var eventMessageMatchers = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

    await().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasCreateEventMessageFor(instance));
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

  public static void instanceDeletedMessagePublished(JsonObject instance) {
    final String instanceId = instance.getString("id");

    final var eventMessageMatchers = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

    await().until(() -> getMessagesForInstance(instanceId),
      eventMessageMatchers.hasDeleteEventMessageFor(instance));
  }

  public static void noInstanceDeletedMessagePublished(String instanceId) {
    final var eventMessageMatchers = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

    await().during(1, SECONDS)
      .until(() -> getMessagesForInstance(instanceId),
        eventMessageMatchers.hasNoDeleteEventMessage());
  }

  public static void assertRemoveAllEventForInstance() {
    await()
      .until(() -> getInstanceEvents(NULL_INSTANCE_ID).size(), greaterThan(0));

    assertRemoveAllEvent(getLastInstanceEvent(NULL_INSTANCE_ID));
  }

  public static void assertRemoveEventForAuthority(JsonObject authority) {
    final String id = authority.getString("id");

    await().until(() -> hasRemoveEvent(getAuthorityEvents(id), authority));
  }

  public static void assertRemoveAllEventForAuthority() {
    await()
      .until(() -> getAuthorityEvents(NULL_INSTANCE_ID).size(), greaterThan(0));

    assertRemoveAllEvent(getLastAuthorityEvent(NULL_INSTANCE_ID));
  }

  public static void assertUpdateEventForInstance(JsonObject oldInstance, JsonObject newInstance) {
    final String instanceId = oldInstance.getString("id");

    await()
      .until(() -> hasUpdateEvent(getInstanceEvents(instanceId), oldInstance, newInstance));
  }

  public static void assertUpdateEventForAuthority(JsonObject oldAuthority, JsonObject newAuthority) {
    final String id = oldAuthority.getString("id");

    await().until(() -> hasUpdateEvent(getAuthorityEvents(id), oldAuthority, newAuthority));
  }

  public static void assertCreateEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);

    await()
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
    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    final JsonObject expectedItem = addInstanceIdForItem(item, instanceIdForItem);
    await().until(() -> hasRemoveEvent(getItemEvents(instanceIdForItem, itemId), expectedItem));
  }

  public static void assertRemoveAllEventForItem() {
    await()
      .until(() -> getItemEvents(NULL_INSTANCE_ID, null).size(), greaterThan(0));

    assertRemoveAllEvent(getLastItemEvent(NULL_INSTANCE_ID, null));
  }

  public static void assertUpdateEventForItem(JsonObject oldItem, JsonObject newItem) {
    assertUpdateEventForItem(oldItem, newItem, getInstanceIdForItem(oldItem));
  }

  public static void assertUpdateEventForItem(JsonObject oldItem, JsonObject newItem, String oldInstanceId) {
    final String itemId = newItem.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(newItem);

    await().until(() -> {
      for (var event : getItemEvents(instanceIdForItem, itemId)) {
        try {
          // Domain event for item has an extra 'instanceId' property for
          // old/new object, the property does not exist in schema,
          // so we have to add it manually
          assertUpdateEvent(event,
              addInstanceIdForItem(oldItem, oldInstanceId),
              addInstanceIdForItem(newItem, instanceIdForItem));
          return true;
        } catch (AssertionError e) {
          // ignore, check next event
        }
      }
      return false;
    });
  }

  public static void assertCreateEventForHolding(JsonObject hr) {
    final String id = hr.getString("id");
    final String instanceId = hr.getString("instanceId");

    await()
      .until(() -> getHoldingsEvents(instanceId, id).size(), greaterThan(0));

    assertCreateEvent(getFirstHoldingEvent(instanceId, id), hr);
  }

  public static void assertRemoveEventForHolding(JsonObject hr) {
    final String id = hr.getString("id");
    final String instanceId = hr.getString("instanceId");

    await().until(() -> hasRemoveEvent(getHoldingsEvents(instanceId, id), hr));
  }

  public static void assertRemoveAllEventForHolding() {
    await()
      .until(() -> getHoldingsEvents(NULL_INSTANCE_ID, null).size(), greaterThan(0));

    assertRemoveAllEvent(getLastHoldingEvent(NULL_INSTANCE_ID, null));
  }

  public static void assertUpdateEventForHolding(JsonObject oldHr, JsonObject newHr) {
    final String id = newHr.getString("id");
    final String newInstanceId = newHr.getString("instanceId");

    await().until(() -> hasUpdateEvent(getHoldingsEvents(newInstanceId, id), oldHr, newHr));
  }

  private static String getInstanceIdForItem(JsonObject newItem) {
    final UUID holdingsRecordId = fromString(getHoldingsRecordIdForItem(newItem));

    return holdingsClient.getById(holdingsRecordId).getJson().getString("instanceId");
  }

  private static String getHoldingsRecordIdForItem(JsonObject item) {
    return item.getString("holdingsRecordId");
  }

  private static JsonObject addInstanceIdForItem(JsonObject item, String instanceId) {
    return item.copy().put("instanceId", instanceId);
  }
}
