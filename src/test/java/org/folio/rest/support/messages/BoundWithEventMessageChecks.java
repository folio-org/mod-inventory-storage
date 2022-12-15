package org.folio.rest.support.messages;

import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.json.JsonObject;

public class BoundWithEventMessageChecks {
  private static final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  private BoundWithEventMessageChecks() { }

  public static boolean hasPublishedBoundWithHoldingsRecordIds(UUID id1,
    UUID id2, UUID id3) {
    List<String> holdingsRecordIds = List.of(id1.toString(), id2.toString(),
      id3.toString());
    List<String> publishedHoldingsRecordIds = FakeKafkaConsumer.getAllPublishedBoundWithEvents().stream()
      .filter(json -> json.containsKey("new"))
      .map(json -> json.getJsonObject("new").getString("holdingsRecordId"))
      .collect(Collectors.toList());
    return publishedHoldingsRecordIds.containsAll(holdingsRecordIds);
  }

  public static void boundWithCreatedMessagePublished(JsonObject boundWith,
    String instanceId) {

    // Bound With messages are published with the instance ID as the key
    // and that property is not part of the record, so must be provided separately
    awaitAtMost().until(() -> FakeKafkaConsumer.getMessagesForBoundWith(instanceId),
      eventMessageMatchers.hasCreateEventMessageFor(
        addInstanceIdToBoundWith(boundWith, instanceId)));
  }

  private static JsonObject addInstanceIdToBoundWith(JsonObject boundWith, String instanceId) {
    // Event for bound with has an extra 'instanceId' property for
    // old/new object, the property does not exist in schema,
    // so we have to add it manually
    return boundWith.copy().put("instanceId", instanceId);
  }
}
