package org.folio.rest.support.matchers;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static java.util.UUID.fromString;
import static org.folio.kafka.KafkaHeaderUtils.kafkaHeadersToMap;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.api.TestBase.holdingsClient;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.JsonObjectMatchers.equalsIgnoringMetadata;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getItemEvents;
import static org.folio.rest.support.kafka.FakeKafkaConsumer.getLastItemEvent;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

public final class DomainEventAssertions {
  private DomainEventAssertions() { }

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

  private static void assertHeaders(List<KafkaHeader> headers) {
    final MultiMap caseInsensitiveMap = caseInsensitiveMultiMap()
      .addAll(kafkaHeadersToMap(headers));

    assertEquals(2, caseInsensitiveMap.size());
    assertEquals(TENANT_ID, caseInsensitiveMap.get(TENANT));
    assertEquals(vertxUrl("").toString(), caseInsensitiveMap.get(URL));
  }

  public static void assertRemoveEventForItem(JsonObject item) {
    final String itemId = item.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(item);
    // Domain event for item has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    final JsonObject expectedItem = addInstanceIdForItem(item, instanceIdForItem);
    awaitAtMost().until(() -> hasRemoveEvent(getItemEvents(instanceIdForItem, itemId), expectedItem));
  }

  public static void assertRemoveAllEventForItem() {
    awaitAtMost()
      .until(() -> getItemEvents(NULL_ID, null).size(), greaterThan(0));

    assertRemoveAllEvent(getLastItemEvent(NULL_ID, null));
  }

  public static void assertUpdateEventForItem(JsonObject oldItem, JsonObject newItem) {
    assertUpdateEventForItem(oldItem, newItem, getInstanceIdForItem(oldItem));
  }

  public static void assertUpdateEventForItem(JsonObject oldItem, JsonObject newItem, String oldInstanceId) {
    final String itemId = newItem.getString("id");
    final String instanceIdForItem = getInstanceIdForItem(newItem);

    awaitAtMost().until(() -> {
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
