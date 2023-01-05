package org.folio.rest.support.messages;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.support.AwaitConfiguration.awaitAtMost;
import static org.folio.rest.support.AwaitConfiguration.awaitDuring;
import static org.folio.services.domainevent.CommonDomainEventPublisher.NULL_ID;
import static org.folio.utility.ModuleUtility.vertxUrl;
import static org.folio.utility.RestUtility.TENANT_ID;

import org.folio.rest.support.kafka.FakeKafkaConsumer;
import org.folio.rest.support.messages.matchers.EventMessageMatchers;

import io.vertx.core.json.JsonObject;

public class HoldingsEventMessageChecks {
  private final EventMessageMatchers eventMessageMatchers
    = new EventMessageMatchers(TENANT_ID, vertxUrl(""));

  private final FakeKafkaConsumer kafkaConsumer;

  public HoldingsEventMessageChecks(FakeKafkaConsumer kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
  }

  public void createdMessagePublished(JsonObject holdings) {
    final var holdingsId = getId(holdings);
    final var instanceId = getInstanceId(holdings);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(instanceId, holdingsId),
      eventMessageMatchers.hasCreateEventMessageFor(holdings));
  }

  public void updatedMessagePublished(JsonObject oldHoldings,
    JsonObject newHoldings) {

    final var holdingsId = getId(newHoldings);
    final var instanceId = getInstanceId(newHoldings);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(instanceId, holdingsId),
      eventMessageMatchers.hasUpdateEventMessageFor(oldHoldings, newHoldings));
  }

  public void noHoldingsUpdatedMessagePublished(String instanceId,
    String holdingsId) {

    awaitDuring(1, SECONDS)
      .until(() -> kafkaConsumer.getMessagesForHoldings(instanceId, holdingsId),
        eventMessageMatchers.hasNoUpdateEventMessage());
  }

  public void deletedMessagePublished(JsonObject holdings) {
    final var holdingsId = getId(holdings);
    final var instanceId = getInstanceId(holdings);

    awaitAtMost().until(() -> kafkaConsumer.getMessagesForHoldings(instanceId, holdingsId),
      eventMessageMatchers.hasDeleteEventMessageFor(holdings));
  }

  public void allHoldingsDeletedMessagePublished() {
    awaitAtMost()
      .until(() -> kafkaConsumer.getMessagesForHoldings(NULL_ID, null),
        eventMessageMatchers.hasDeleteAllEventMessage());
  }

  private static String getId(JsonObject holdings) {
    return holdings.getString("id");
  }

  private static String getInstanceId(JsonObject holdings) {
    return holdings.getString("instanceId");
  }
}